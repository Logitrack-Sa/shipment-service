package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.ShipmentServiceApplication;
import com.logitrack.shipment.client.CourierProperties;
import com.logitrack.shipment.security.JwtProperties;
import com.logitrack.shipment.support.JwtTestSupport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.MapPropertySource;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

class ConfigClientIntegrationTest {
    HttpServer server;
    List<String> paths;

    @BeforeEach void setup() throws Exception {
        paths = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            Map<String,Object> source = new LinkedHashMap<>();
            source.put("server.port", 0);
            source.put("spring.datasource.url", "jdbc:h2:mem:config-" + UUID.randomUUID() + ";MODE=MySQL");
            source.put("spring.datasource.username", "sa");
            source.put("spring.datasource.password", ""); // Solo H2 efímero de prueba.
            source.put("spring.jpa.hibernate.ddl-auto", "create-drop");
            source.put("spring.flyway.enabled", false);
            source.put("logitrack.fee.base", "1000");
            source.put("logitrack.fee.price-per-kg", "250");
            source.put("logitrack.fee.volumetric-divisor", "5000");
            source.put("logitrack.courier.base-url", "http://courier-fixture.test:8082");
            source.put("logitrack.courier.connect-timeout", "250ms");
            source.put("logitrack.courier.read-timeout", "500ms");
            source.put("logitrack.security.public-key-location", JwtTestSupport.PUBLIC_KEY.toUri().toString());
            source.put("logitrack.security.key-id", JwtTestSupport.KID);
            source.put("logitrack.security.issuer", JwtTestSupport.ISSUER);
            source.put("logitrack.security.audience", JwtTestSupport.AUDIENCE);
            source.put("logging.level.com.logitrack", "WARN");
            var body = Map.of("name", "shipment-service", "profiles", List.of("dev", "prod"),
                    "propertySources", List.of(Map.of("name", "git-fixture", "source", source)));
            byte[] bytes = new ObjectMapper().writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach void cleanup() { server.stop(0); }

    SpringApplicationBuilder application(int port) {
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return new SpringApplicationBuilder(ShipmentServiceApplication.class).environment(environment)
                .properties("CONFIG_SERVER_URL=http://127.0.0.1:" + port);
    }

    @ParameterizedTest @ValueSource(strings = {"dev", "prod"})
    void startsFullApplicationWithRemoteProfile(String profile) {
        try (var context = application(server.getAddress().getPort()).run("--spring.profiles.active=" + profile)) {
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("0");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:config-");
            assertThat(context.getBean(CourierProperties.class).baseUrl().toString()).isEqualTo("http://courier-fixture.test:8082");
            assertThat(context.getBean(JwtProperties.class).issuer()).isEqualTo(JwtTestSupport.ISSUER);
            assertThat(context.getEnvironment().getProperty("logging.level.com.logitrack")).isEqualTo("WARN");
            assertThat(paths).anyMatch(path -> path.contains("shipment-service/" + profile));
        }
    }

    @Test void missingConfigServerStopsStartup() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        assertThatThrownBy(() -> application(port).run("--spring.profiles.active=prod"))
                .hasStackTraceContaining("ConfigClientFailFastException");
    }
}
