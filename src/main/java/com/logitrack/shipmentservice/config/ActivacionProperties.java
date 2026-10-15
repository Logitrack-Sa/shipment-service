package com.logitrack.shipmentservice.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.activacion")
public record ActivacionProperties(
        @NotNull(message = "es obligatoria")
                @Positive(message = "debe ser un valor positivo")
                Long tokenValidezHoras) {}
