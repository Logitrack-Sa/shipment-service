package com.logitrack.shipment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.entity.*;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.*;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShipmentSecurityIntegrationTest extends JwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CustomerRepository customers;
    @Autowired ShipmentRepository shipments;
    @Autowired ShipmentStatusHistoryRepository history;
    @MockitoBean CourierLookup couriers;
    Customer customerA, customerB;
    Shipment a, b;

    @BeforeEach void setup() {
        history.deleteAll(); shipments.deleteAll(); customers.deleteAll();
        customerA = customers.saveAndFlush(new Customer("A", "a@test.com", "123"));
        customerB = customers.saveAndFlush(new Customer("B", "b@test.com", "123"));
        a = seed(customerA, "LT-0000000001", identityLink("repartidor-a", "courierId"));
        b = seed(customerB, "LT-0000000002", identityLink("repartidor-b", "courierId"));
        history.saveAndFlush(new ShipmentStatusHistory(a, null, ShipmentStatus.PENDIENTE, Instant.now(), "Alta"));
    }

    Shipment seed(Customer customer, String tracking, UUID courierId) {
        var entity = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customer, tracking, new BigDecimal("2200"));
        entity.assignCourier(courierId);
        return shipments.saveAndFlush(entity);
    }

    String bearer(String name) {
        return "Bearer " + token(name, name.startsWith("cliente") ? Map.of("customerId",
                (name.endsWith("a") ? customerA : customerB).getId().toString()) : Map.of());
    }

    @Test void requiresTokenAndReturnsStructured401() throws Exception {
        mvc.perform(get("/api/v1/shipments")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test void rejectsBadSignatureExpiryIssuerAudienceKidAndClaims() throws Exception {
        var valid = token("admin");
        var pieces = valid.split("[.]");
        var signature = Base64.getUrlDecoder().decode(pieces[2]); signature[0] ^= 1;
        var tampered = pieces[0] + "." + pieces[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        for (String invalid : List.of("bad.jwt", tampered,
                token("admin", Map.of("exp", Date.from(Instant.now().minusSeconds(300)))),
                token("admin", Map.of("iss", "https://other.test")),
                token("admin", Map.of("aud", List.of("other"))),
                token("admin", Map.of("roles", List.of("OWNER"))),
                token("admin", Map.of("roles", "ADMIN")),
                token("admin", Map.of("sub", "bad-uuid")),
                token("admin", java.util.Collections.singletonMap("exp", null)),
                token("admin", java.util.Collections.singletonMap("iat", null)),
                signedToken("admin", Map.of(), com.nimbusds.jose.JWSAlgorithm.RS256, "wrong-kid"),
                signedToken("admin", Map.of(), com.nimbusds.jose.JWSAlgorithm.RS512, KID))) {
            mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized());
        }
        var changedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"RS256\",\"kid\":\"unknown\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + changedHeader + "." + pieces[1] + "." + pieces[2]))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"cliente-a", "repartidor-a"})
    void enforcesOwnershipForIdTrackingAndHistory(String identity) throws Exception {
        for (String path : List.of("/" + a.getId(), "/tracking/" + a.getTrackingCode(), "/" + a.getId() + "/history")) {
            mvc.perform(get("/api/v1/shipments" + path).header("Authorization", bearer(identity)))
                    .andExpect(status().isOk());
        }
        for (String path : List.of("/" + b.getId(), "/tracking/" + b.getTrackingCode(), "/" + b.getId() + "/history")) {
            mvc.perform(get("/api/v1/shipments" + path).header("Authorization", bearer(identity)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"cliente-a", "cliente-b", "repartidor-a", "repartidor-b"})
    void filtersRowsAndTotalBeforePagination(String identity) throws Exception {
        var expected = identity.endsWith("a") ? a : b;
        mvc.perform(get("/api/v1/shipments?size=1&status=PENDIENTE").header("Authorization", bearer(identity)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expected.getId().toString()));
        mvc.perform(get("/api/v1/shipments?size=1&page=1").header("Authorization", bearer(identity)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void adminSeesAllAndClientCannotReadCustomerIdentities() throws Exception {
        mvc.perform(get("/api/v1/shipments").header("Authorization", bearer("admin")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        for (String path : List.of("/api/v1/customers", "/api/v1/customers/" + customerA.getId())) {
            mvc.perform(get(path).header("Authorization", bearer("cliente-a"))).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", bearer("admin"))).andExpect(status().isOk());
        }
    }

    @Test void clientCreatesOnlyForOwnCustomerAndCannotChangeStateOrAssignment() throws Exception {
        var request = json.valueToTree(ShipmentFixtures.request());
        ((com.fasterxml.jackson.databind.node.ObjectNode)request).put("customerId", customerA.getId().toString());
        mvc.perform(post("/api/v1/shipments").header("Authorization", bearer("cliente-a"))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isCreated());
        ((com.fasterxml.jackson.databind.node.ObjectNode)request).put("customerId", customerB.getId().toString());
        mvc.perform(post("/api/v1/shipments").header("Authorization", bearer("cliente-a"))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments").header("Authorization", bearer("repartidor-a"))
                .contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/shipments/" + a.getId() + "/courier").header("Authorization", bearer("cliente-a"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"courierId\":\"" + b.getCourierId() + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/shipments/" + a.getId() + "/status").header("Authorization", bearer("cliente-a"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_RUTA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/shipments/" + a.getId() + "/cancellation").header("Authorization", bearer("cliente-a")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(couriers);
    }

    @Test void courierChangesOnlyAssignedShipmentAndCannotCancel() throws Exception {
        String own = "/api/v1/shipments/" + a.getId();
        mvc.perform(patch(own + "/status").header("Authorization", bearer("repartidor-b"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_RUTA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(own + "/status").header("Authorization", bearer("repartidor-a"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELADO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(own + "/status").header("Authorization", bearer("repartidor-a"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_RUTA\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(own + "/status").header("Authorization", bearer("repartidor-a"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ENTREGADO\"}"))
                .andExpect(status().isOk());
        assertThat(history.findByShipmentIdOrderByChangedAtAsc(a.getId())).hasSize(3);
        assertThat(shipments.findById(a.getId()).orElseThrow().getLastModifiedBy())
                .isEqualTo(identityLink("repartidor-a", "sub").toString());
    }

    @Test void missingOrMalformedAssociationDeniesWithoutListing() throws Exception {
        for (String id : List.of("invalid", "")) {
            mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + token("cliente-a", Map.of("customerId", id))))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest @ValueSource(ints = {401,403,503})
    void remoteFailureRollsBackStateAndHistory(int code) throws Exception {
        doThrow(new com.logitrack.shipment.exception.RemoteCourierException(
                org.springframework.http.HttpStatus.valueOf(code), "REMOTE_FAILURE"))
                .when(couriers).requireActive(a.getCourierId());
        mvc.perform(patch("/api/v1/shipments/" + a.getId() + "/status").header("Authorization", bearer("admin"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"EN_RUTA\"}"))
                .andExpect(status().is(code));
        assertThat(shipments.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.PENDIENTE);
        assertThat(history.findByShipmentIdOrderByChangedAtAsc(a.getId())).hasSize(1);
    }
}
