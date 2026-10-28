package com.logitrack.shipment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.exception.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class HttpCourierLookupTest {
    static final UUID ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    HttpServer server;
    HttpCourierLookup client;
    int responseStatus = 200;
    String responseBody;
    long delay;
    String receivedPath;
    String receivedBearer;
    AtomicInteger requests;
    java.util.concurrent.ExecutorService executor;

    @BeforeEach void setup() throws Exception {
        requests = new AtomicInteger();
        var jwt = Jwt.withTokenValue("validated-bearer").header("alg", "RS256").subject("user").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        responseBody = fixture("available");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            receivedPath = exchange.getRequestURI().getPath();
            receivedBearer = exchange.getRequestHeaders().getFirst("Authorization");
            try {
                Thread.sleep(delay);
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        client = clientAt(server.getAddress().getPort());
    }

    HttpCourierLookup clientAt(int port) {
        var props = new CourierProperties(URI.create("http://127.0.0.1:" + port),
                Duration.ofMillis(200), Duration.ofMillis(300));
        return new HttpCourierLookup(new CourierClientConfig().courierRestClient(RestClient.builder(), props));
    }

    @AfterEach void cleanup() {
        server.stop(0);
        executor.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    @Test void checksAvailabilityAndPropagatesOnlyValidatedBearer() {
        client.requireAvailable(ID);
        assertThat(receivedPath).isEqualTo("/api/v1/couriers/" + ID + "/availability");
        assertThat(receivedBearer).isEqualTo("Bearer validated-bearer");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void checksInternalStatusWithoutAdministrativeEndpoint() throws Exception {
        responseBody = fixture("active-status");
        client.requireActive(ID);
        assertThat(receivedPath).isEqualTo("/api/v1/internal/couriers/" + ID + "/status");
        assertThat(receivedBearer).isEqualTo("Bearer validated-bearer");
    }

    @Test void rejectsInactiveWithoutRetry() throws Exception {
        responseBody = fixture("inactive");
        assertThatThrownBy(() -> client.requireAvailable(ID)).isInstanceOf(CourierNotActiveException.class);
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"401,COURIER_UNAUTHORIZED", "403,COURIER_FORBIDDEN", "500,COURIER_UNAVAILABLE",
            "503,COURIER_UNAVAILABLE", "302,COURIER_UNAVAILABLE"})
    void translatesRemoteFailures(int status, String code) {
        responseStatus = status;
        assertThatThrownBy(() -> client.requireAvailable(ID)).isInstanceOfSatisfying(RemoteCourierException.class,
                ex -> assertThat(ex.code()).isEqualTo(code));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void translates404() throws Exception {
        responseStatus = 404;
        responseBody = fixture("missing");
        assertThatThrownBy(() -> client.requireAvailable(ID)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void translates422() throws Exception {
        responseStatus = 422;
        responseBody = fixture("invalid");
        assertThatThrownBy(() -> client.requireAvailable(ID)).isInstanceOf(CourierNotActiveException.class);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "null", "not-json",
            "{\"courierId\":\"20000000-0000-0000-0000-000000000099\",\"status\":\"ACTIVO\",\"available\":true}",
            "{\"courierId\":\"20000000-0000-0000-0000-000000000001\",\"status\":\"UNKNOWN\",\"available\":true}",
            "{\"courierId\":\"20000000-0000-0000-0000-000000000001\",\"status\":\"INACTIVO\",\"available\":true}"})
    void rejectsMalformedOrInconsistentPayload(String body) {
        responseBody = body;
        assertUnavailable(() -> client.requireAvailable(ID));
    }

    @Test void timesOutWithoutRetry() {
        delay = 1200;
        assertUnavailable(() -> client.requireAvailable(ID));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void handlesConnectionRefused() throws Exception {
        int unused;
        try (var socket = new ServerSocket(0)) { unused = socket.getLocalPort(); }
        assertUnavailable(() -> clientAt(unused).requireActive(ID));
    }

    @Test void refusesUnauthenticatedCallBeforeSendingHttp() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> client.requireAvailable(ID)).isInstanceOf(AccessDeniedException.class);
        assertThat(requests.get()).isZero();
    }

    static void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RemoteCourierException.class,
                ex -> assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    static String fixture(String name) throws Exception {
        try (var input = HttpCourierLookupTest.class.getResourceAsStream("/contracts/v1/courier-" + name + ".json")) {
            return new ObjectMapper().readTree(input).get("body").toString();
        }
    }
}
