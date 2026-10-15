package com.logitrack.shipmentservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.limites")
public record LimitesProperties(@Valid @NotNull ExtraccionDiaria extraccionDiaria) {

    public record ExtraccionDiaria(
            @NotNull(message = "es obligatoria")
                    @DecimalMin(value = "0", message = "debe ser un importe mayor o igual a 0")
                    BigDecimal titular,
            @NotNull(message = "es obligatoria")
                    @DecimalMin(value = "0", message = "debe ser un importe mayor o igual a 0")
                    BigDecimal adherente) {}
}
