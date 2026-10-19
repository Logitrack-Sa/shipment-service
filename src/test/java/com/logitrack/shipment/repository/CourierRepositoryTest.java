
package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.CourierStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;

class CourierRepositoryTest extends AbstractMySqlRepositoryTest {

    @Autowired
    private CourierRepository courierRepository;

    @Test
    void shouldFindCouriersByStatusWithPagination() {
        Courier activeOne = new Courier("Carlos", "Moto");
        Courier activeTwo = new Courier("Lucía", "Bicicleta");

        CourierStatus otherStatus = java.util.Arrays.stream(
                CourierStatus.values()
            )
            .filter(status -> status != CourierStatus.ACTIVO)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Se necesita otro estado de repartidor para esta prueba"
            ));

        Courier inactive = new Courier("Pedro", "Auto");
        inactive.changeStatus(otherStatus);

        courierRepository.saveAndFlush(activeOne);
        courierRepository.saveAndFlush(activeTwo);
        courierRepository.saveAndFlush(inactive);

        var result = courierRepository.findByStatus(
            CourierStatus.ACTIVO,
            PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements());
        assertTrue(result.getContent().stream()
            .allMatch(courier ->
                courier.getStatus() == CourierStatus.ACTIVO));
    }
}
