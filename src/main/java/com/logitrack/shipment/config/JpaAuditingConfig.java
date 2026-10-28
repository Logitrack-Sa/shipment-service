package com.logitrack.shipment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "systemAuditor")
public class JpaAuditingConfig {
    @Bean
    AuditorAware<String> systemAuditor() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(auth -> auth.isAuthenticated() && auth instanceof JwtAuthenticationToken)
                .map(Authentication::getName).or(() -> Optional.of("system"));
    }
}
