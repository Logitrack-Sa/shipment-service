package com.logitrack.shipment.config;

import com.logitrack.shipment.service.FeeCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FeeConfig {
    @Bean
    FeeCalculator feeCalculator(FeeProperties properties) {
        return new FeeCalculator(properties.base(), properties.pricePerKg(), properties.volumetricDivisor());
    }
}
