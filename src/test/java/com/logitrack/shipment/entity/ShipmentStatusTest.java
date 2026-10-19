package com.logitrack.shipment.entity;

import com.logitrack.shipment.exception.InvalidStateTransitionException;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la máquina de estados de {@link ShipmentStatus}: las 16 combinaciones de la tabla
 * de transiciones y la mutación de estado de {@link Shipment#changeStatusTo(ShipmentStatus)}.
 */
class ShipmentStatusTest {

    /**
     * Comprueba que {@link ShipmentStatus#canTransitionTo(ShipmentStatus)} coincide con la
     * tabla de transiciones permitidas para cada par origen/destino.
     */
    @ParameterizedTest
    @CsvSource({
            "PENDIENTE, PENDIENTE, false",
            "PENDIENTE, EN_RUTA, true",
            "PENDIENTE, ENTREGADO, false",
            "PENDIENTE, CANCELADO, true",
            "EN_RUTA, PENDIENTE, false",
            "EN_RUTA, EN_RUTA, false",
            "EN_RUTA, ENTREGADO, true",
            "EN_RUTA, CANCELADO, false",
            "ENTREGADO, PENDIENTE, false",
            "ENTREGADO, EN_RUTA, false",
            "ENTREGADO, ENTREGADO, false",
            "ENTREGADO, CANCELADO, false",
            "CANCELADO, PENDIENTE, false",
            "CANCELADO, EN_RUTA, false",
            "CANCELADO, ENTREGADO, false",
            "CANCELADO, CANCELADO, false"
    })
    void describesTheTransitionTable(ShipmentStatus from, ShipmentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    /**
     * Comprueba que {@link Shipment#changeStatusTo(ShipmentStatus)} muta el estado solo en
     * transiciones permitidas y lanza {@link InvalidStateTransitionException} en las inválidas
     * sin modificar el envío.
     */
    @ParameterizedTest
    @CsvSource({
            "PENDIENTE, PENDIENTE, false",
            "PENDIENTE, EN_RUTA, true",
            "PENDIENTE, ENTREGADO, false",
            "PENDIENTE, CANCELADO, true",
            "EN_RUTA, PENDIENTE, false",
            "EN_RUTA, EN_RUTA, false",
            "EN_RUTA, ENTREGADO, true",
            "EN_RUTA, CANCELADO, false",
            "ENTREGADO, PENDIENTE, false",
            "ENTREGADO, EN_RUTA, false",
            "ENTREGADO, ENTREGADO, false",
            "ENTREGADO, CANCELADO, false",
            "CANCELADO, PENDIENTE, false",
            "CANCELADO, EN_RUTA, false",
            "CANCELADO, ENTREGADO, false",
            "CANCELADO, CANCELADO, false"
    })
    void changeStatusToOnlyMutatesAllowedTransitions(ShipmentStatus from, ShipmentStatus to, boolean allowed) {
        Shipment shipment = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipment, "status", from);

        if (allowed) {
            shipment.changeStatusTo(to);
            assertThat(shipment.getStatus()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> shipment.changeStatusTo(to))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThat(shipment.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void rejectsNullTarget() {
        Shipment shipment = ShipmentEntities.shipment();

        assertThatThrownBy(() -> shipment.changeStatusTo(null))
                .isInstanceOf(NullPointerException.class);
    }
}
