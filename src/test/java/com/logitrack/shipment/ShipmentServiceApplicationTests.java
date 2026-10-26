package com.logitrack.shipment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentServiceApplicationTests extends com.logitrack.shipment.support.JwtTestSupport {

    @Test
    void contextLoads() {
    }

}
