package com.logitrack.shipment.security;

import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.support.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.endpoints.web.exposure.include=health,info"
})
@ActiveProfiles("test")
class BusinessListenerHealthWithoutManagementPortTest extends JwtTestSupport {

    @LocalServerPort
    int businessPort;

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    CourierLookup couriers;

    @Test
    void healthStaysAvailableAndTechnicalEndpointsRemainClosed() {
        var health = rest.getForEntity(url("/actuator/health"), String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        var prometheus = rest.getForEntity(url("/actuator/prometheus"), String.class);
        assertThat(prometheus.getStatusCode().value()).isIn(401, 403, 404);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + businessPort + path;
    }
}
