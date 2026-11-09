package com.logitrack.shipment.security;

import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.support.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=127.0.0.1",
        "management.endpoints.web.exposure.include=health,info,prometheus",
        "management.endpoint.prometheus.enabled=true",
        "management.prometheus.metrics.export.enabled=true",
        "management.metrics.tags.application=shipment-service",
        "management.metrics.distribution.percentiles-histogram.http.server.requests=true"
})
@ActiveProfiles("test")
class ManagementListenerSeparationTest extends JwtTestSupport {

    @LocalServerPort
    int businessPort;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    Environment environment;

    @MockitoBean
    CourierLookup couriers;

    @Test
    void managementListenerExposesPrometheusWithApplicationTag() {
        var response = rest.getForEntity(url(managementPort(), "/actuator/prometheus"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application=\"shipment-service\"");
    }

    @Test
    void managementListenerExposesHealth() {
        var response = rest.getForEntity(url(managementPort(), "/actuator/health"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trafficGeneratesHttpPercentilesBuckets() {
        // Tráfico al listener de negocio con JWT
        var headers = new HttpHeaders();
        headers.setBearerAuth(token("repartidor-a"));
        rest.exchange(url(businessPort, "/api/v1/shipments"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // Tráfico al listener de gestión
        rest.getForEntity(url(managementPort(), "/actuator/health"), String.class);

        var response = rest.getForEntity(url(managementPort(), "/actuator/prometheus"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("http_server_requests_seconds_bucket{");
        assertThat(response.getBody()).contains("le=");
        assertThat(response.getBody()).contains("http_server_requests_seconds_count");
    }

    @Test
    void businessListenerDoesNotExposePrometheusOrInfo() {
        for (String path : new String[] { "/actuator/prometheus", "/actuator/info" }) {
            var response = rest.getForEntity(url(businessPort, path), String.class);
            assertThat(response.getStatusCode().value()).isIn(401, 403, 404);
        }
    }

    @Test
    void businessListenerRejectsTechnicalEndpointsEvenWithValidJwt() {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token("repartidor-a"));
        for (String path : new String[] { "/actuator/prometheus", "/actuator/info" }) {
            var response = rest.exchange(url(businessPort, path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertThat(response.getStatusCode().value()).isIn(401, 403, 404);
        }
    }

    @Test
    void administrativeEndpointsAreNotExposed() {
        for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/threaddump" }) {
            for (int port : new int[] { businessPort, managementPort() }) {
                var response = rest.getForEntity(url(port, path), String.class);
                assertThat(response.getStatusCode().value()).isIn(401, 403, 404, 405);
            }
        }
        // POST /actuator/shutdown debe ser rechazado en ambos listeners
        for (int port : new int[] { businessPort, managementPort() }) {
            var postResponse = rest.postForEntity(url(port, "/actuator/shutdown"), null, String.class);
            assertThat(postResponse.getStatusCode().value()).isIn(401, 403, 404, 405);
        }
    }

    private int managementPort() {
        return Integer.parseInt(environment.getRequiredProperty("local.management.port"));
    }

    private String url(int port, String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
