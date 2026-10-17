package com.logitrack.shipment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "systemAuditor")
public class JpaAuditingConfig {
    @Bean
    AuditorAware<String> systemAuditor() {
        return () -> Optional.of("system");
    }
}
