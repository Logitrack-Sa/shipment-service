package com.logitrack.shipment.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.*;

public abstract class JwtTestSupport {
    public static final String ISSUER = "https://issuer.test";
    public static final String AUDIENCE = "shipment-service";
    public static final String KID = "test-ephemeral";
    public static final KeyPair KEYS;
    public static final Path PUBLIC_KEY;
    private static final JsonNode IDENTITIES;
    static {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEYS = generator.generateKeyPair();
            PUBLIC_KEY = Files.createTempFile("shipment-test-public-", ".pem");
            PUBLIC_KEY.toFile().deleteOnExit();
            Files.writeString(PUBLIC_KEY, "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(KEYS.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n");
            try (var input = JwtTestSupport.class.getResourceAsStream("/contracts/v1/identities.json")) {
                IDENTITIES = new ObjectMapper().readTree(input);
            }
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("logitrack.security.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("logitrack.security.key-id", () -> KID);
        registry.add("logitrack.security.issuer", () -> ISSUER);
        registry.add("logitrack.security.audience", () -> AUDIENCE);
    }

    public static UUID identityLink(String name, String field) {
        return UUID.fromString(IDENTITIES.get(name).get(field).asText());
    }

    public static String token(String name) { return token(name, Map.of()); }

    public static String token(String name, Map<String, Object> overrides) {
        return signedToken(name, overrides, JWSAlgorithm.RS256, KID);
    }

    public static String signedToken(String name, Map<String, Object> overrides, JWSAlgorithm algorithm, String kid) {
        try {
            JsonNode identity = IDENTITIES.get(name);
            var claims = new JWTClaimsSet.Builder().subject(identity.get("sub").asText()).issuer(ISSUER)
                    .audience(List.of(AUDIENCE, "courier-service")).issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(600)));
            identity.fields().forEachRemaining(entry -> {
                if (entry.getKey().equals("roles")) {
                    claims.claim("roles", new ObjectMapper().convertValue(entry.getValue(), List.class));
                } else if (!entry.getKey().equals("sub")) claims.claim(entry.getKey(), entry.getValue().asText());
            });
            overrides.forEach(claims::claim);
            var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(kid).build(), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
