package com.logitrack.shipment.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) throws IOException {
        try (var stream = properties.publicKeyLocation().getInputStream()) {
            var decoder = NimbusJwtDecoder.withPublicKey(RsaKeyConverters.x509().convert(stream))
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(properties.issuer()), new JwtContractValidator(properties)));
            return decoder;
        }
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrors errors) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/", "/docs/**", "/swagger-ui.html", "/swagger-ui/**",
                            "/v3/api-docs", "/v3/api-docs/**", "/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/api/v1/customers", "/api/v1/customers/**").hasRole("ADMIN")
                    .requestMatchers("/api/v1/shipments", "/api/v1/shipments/**")
                            .hasAnyRole("ADMIN", "CLIENTE", "REPARTIDOR")
                    .anyRequest().denyAll())
                .exceptionHandling(handler -> handler
                    .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((req, res, ex) -> errors.write(req, res, HttpStatus.FORBIDDEN)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                    .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, HttpStatus.UNAUTHORIZED))
                    .accessDeniedHandler((req, res, ex) -> errors.write(req, res, HttpStatus.FORBIDDEN)))
                .build();
    }
}
