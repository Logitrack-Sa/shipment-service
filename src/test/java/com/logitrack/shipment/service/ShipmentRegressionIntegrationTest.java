package com.logitrack.shipment.service;

import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.entity.*;
import com.logitrack.shipment.exception.*;
import com.logitrack.shipment.repository.*;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentRegressionIntegrationTest extends JwtTestSupport {
    @Autowired ShipmentService service;
    @Autowired ShipmentRepository shipments;
    @Autowired CustomerRepository customers;
    @Autowired ShipmentStatusHistoryRepository history;
    @Autowired JwtDecoder decoder;
    @MockitoBean CourierLookup couriers;
    UUID customerId;
    UUID courierId = identityLink("repartidor-a", "courierId");

    @BeforeEach void setup() {
        history.deleteAll(); shipments.deleteAll(); customers.deleteAll();
        customerId = customers.saveAndFlush(new Customer("Regresión", "regression@test.com", "123")).getId();
        authenticate("admin", Map.of());
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    void authenticate(String name, Map<String,Object> claims) {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles"); authorities.setAuthorityPrefix("ROLE_");
        var jwt = decoder.decode(token(name, claims));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities.convert(jwt)));
    }

    CreateShipmentRequest request(BigDecimal weight, BigDecimal width, BigDecimal height, BigDecimal length) {
        var fixture = ShipmentFixtures.request();
        return new CreateShipmentRequest(customerId, fixture.origin(), fixture.destination(), weight, width, height, length);
    }

    UUID create() {
        return service.createShipment(request(new BigDecimal("2.5"), new BigDecimal("30"),
                new BigDecimal("20"), new BigDecimal("40"))).id();
    }

    @ParameterizedTest
    @CsvSource({"10,10,10,10,3500.00", "2.5,30,20,40,2200.00", "0.1,10.01,10.01,10.01,1050.15"})
    void preservesFeeTrackingAndQueryAfterDatabaseRoundTrip(BigDecimal weight, BigDecimal width,
            BigDecimal height, BigDecimal length, BigDecimal expected) {
        var created = service.createShipment(request(weight, width, height, length));
        var loaded = service.findByTrackingCode(created.trackingCode());
        assertThat(loaded.fee()).isEqualByComparingTo(expected);
        assertThat(loaded.trackingCode()).matches("LT-[A-Z0-9]{10}");
        assertThat(service.findById(created.id())).isEqualTo(loaded);
        assertThat(history.findByShipmentIdOrderByChangedAtAsc(created.id())).hasSize(1);
    }

    @Test void deliversAndKeepsAuditWithNoTerminalMutation() {
        var id = create();
        service.assignCourier(id, courierId);
        authenticate("repartidor-a", Map.of());
        service.changeStatus(id, ShipmentStatus.EN_RUTA, "Retiro");
        service.changeStatus(id, ShipmentStatus.ENTREGADO, "Entrega");
        var before = snapshot(id);
        assertThatThrownBy(() -> service.changeStatus(id, ShipmentStatus.EN_RUTA, "Reintento"))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(history.findByShipmentIdOrderByChangedAtAsc(id))
                .extracting(ShipmentStatusHistory::getNewStatus)
                .containsExactly(ShipmentStatus.PENDIENTE, ShipmentStatus.EN_RUTA, ShipmentStatus.ENTREGADO);
        assertThat(shipments.findById(id).orElseThrow().getLastModifiedBy())
                .isEqualTo(identityLink("repartidor-a", "sub").toString());
    }

    @Test void cancellationPreservesCourierAndRejectsRepeatedChanges() {
        var id = create();
        service.assignCourier(id, courierId);
        service.cancel(id);
        var before = snapshot(id);
        assertThat(before.courierId()).isEqualTo(courierId);
        assertThat(before.status()).isEqualTo(ShipmentStatus.CANCELADO);
        assertThat(before.historyIds()).hasSize(2);
        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(ShipmentCannotBeCancelledException.class);
        assertThat(snapshot(id)).isEqualTo(before);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={401,403,503})
    void remoteFailureDoesNotChangeAssignmentVersionTimestampOrHistory(int code) {
        var id = create();
        service.assignCourier(id, courierId);
        var before = snapshot(id);
        var another = UUID.randomUUID();
        doThrow(new RemoteCourierException(HttpStatus.valueOf(code), "REMOTE_FAILURE"))
                .when(couriers).requireAvailable(another);
        assertThatThrownBy(() -> service.assignCourier(id, another)).isInstanceOf(RemoteCourierException.class);
        assertThat(snapshot(id)).isEqualTo(before);
    }

    @Test void deniedPermissionLeavesEveryPersistentFieldUntouched() {
        var id = create();
        var before = snapshot(id);
        authenticate("cliente-b", Map.of("customerId", UUID.randomUUID().toString()));
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assignCourier(id, courierId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(AccessDeniedException.class);
        assertThat(snapshot(id)).isEqualTo(before);
        verifyNoInteractions(couriers);
    }

    Snapshot snapshot(UUID id) {
        var shipment = shipments.findById(id).orElseThrow();
        return new Snapshot(shipment.getCourierId(), shipment.getStatus(), shipment.getVersion(), shipment.getUpdatedAt(),
                history.findByShipmentIdOrderByChangedAtAsc(id).stream().map(ShipmentStatusHistory::getId).toList());
    }

    record Snapshot(UUID courierId, ShipmentStatus status, Long version, Instant updatedAt, List<UUID> historyIds) { }
}
