package com.logitrack.shipment.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dobles de desarrollo, enlazados exclusivamente a loopback y fuera del JAR productivo. */
final class LocalDependencies implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ObjectMapper json = new ObjectMapper();

    LocalDependencies() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            Object body;
            int status = 200;
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/shipment-service/")) {
                body = Map.of("name", "shipment-service", "profiles", List.of("dev"), "propertySources",
                        List.of(Map.of("name", "DEMO-LOCAL-SIMULADA", "source", configuration())));
            } else if (path.matches("/api/v1/(internal/)?couriers/[a-f0-9-]{36}/(availability|status)")) {
                String id = path.split("/")[path.split("/").length - 2];
                boolean known = id.equals(JwtTestSupport.identityLink("repartidor-a", "courierId").toString())
                        || id.equals(JwtTestSupport.identityLink("repartidor-b", "courierId").toString());
                if (!known) { status = 404; body = Map.of("code", "RESOURCE_NOT_FOUND"); }
                else if (!String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Bearer ")) {
                    status = 401; body = Map.of("code", "UNAUTHORIZED");
                } else body = path.endsWith("availability")
                        ? Map.of("courierId", id, "status", "ACTIVO", "available", true)
                        : Map.of("courierId", id, "status", "ACTIVO");
            } else { status = 404; body = Map.of("code", "RESOURCE_NOT_FOUND"); }
            byte[] bytes = json.writeValueAsBytes(body);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    Map<String,Object> configuration() {
        return Map.ofEntries(
                Map.entry("server.port", 8081), Map.entry("server.address", "127.0.0.1"),
                Map.entry("spring.datasource.url", "jdbc:h2:mem:shipment_demo;MODE=MySQL;DB_CLOSE_DELAY=-1"),
                Map.entry("spring.datasource.username", "sa"), Map.entry("spring.datasource.password", ""),
                Map.entry("spring.jpa.hibernate.ddl-auto", "create-drop"), Map.entry("spring.flyway.enabled", false),
                Map.entry("logitrack.fee.base", "1000"), Map.entry("logitrack.fee.price-per-kg", "250"),
                Map.entry("logitrack.fee.volumetric-divisor", "5000"),
                Map.entry("logitrack.courier.base-url", url()), Map.entry("logitrack.courier.connect-timeout", "500ms"),
                Map.entry("logitrack.courier.read-timeout", "1s"),
                Map.entry("logitrack.security.public-key-location", JwtTestSupport.PUBLIC_KEY.toUri().toString()),
                Map.entry("logitrack.security.key-id", JwtTestSupport.KID), Map.entry("logitrack.security.issuer", JwtTestSupport.ISSUER),
                Map.entry("logitrack.security.audience", JwtTestSupport.AUDIENCE));
    }

    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
