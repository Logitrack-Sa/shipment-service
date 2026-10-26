package com.logitrack.shipment.client;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("logitrack.courier")
public record CourierProperties(@NotNull URI baseUrl, @NotNull Duration connectTimeout,
                                @NotNull Duration readTimeout) {
    @AssertTrue(message = "Courier requiere URL HTTP sin credenciales y timeouts positivos")
    public boolean isValid() {
        return baseUrl != null && baseUrl.getHost() != null && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null && baseUrl.getFragment() == null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                && connectTimeout != null && connectTimeout.isPositive()
                && readTimeout != null && readTimeout.isPositive();
    }
}
