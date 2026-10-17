package com.logitrack.shipment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShipmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShipmentServiceApplication.class, args);
    }

}
