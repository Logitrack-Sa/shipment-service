package com.logitrack.shipmentservice.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.comisiones")
public record ComisionesProperties(
        @NotNull(message = "es obligatoria")
                @DecimalMin(value = "0", message = "debe ser un importe mayor o igual a 0")
                BigDecimal cuentaCorriente,
        @NotNull(message = "es obligatoria")
                @DecimalMin(value = "0", message = "debe ser un importe mayor o igual a 0")
                BigDecimal cajaAhorro,
        @NotBlank(message = "es obligatoria") @ValidCron String cron) {}
