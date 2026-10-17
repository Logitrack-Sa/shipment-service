package com.logitrack.shipment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {
    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
