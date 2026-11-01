package com.logitrack.shipment.client;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("logitrack.courier")
public record CourierProperties(
        URI baseUrl,
        @NotNull @DefaultValue("500ms") Duration connectTimeout,
        @NotNull @DefaultValue("1s") Duration readTimeout,
        @NotBlank @DefaultValue("courier-service") String serviceId) {

    @ConstructorBinding
    public CourierProperties {
    }

    public CourierProperties(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
        this(baseUrl, connectTimeout, readTimeout, "courier-service");
    }

    @AssertTrue(message = "Courier requiere timeouts positivos y, si se define baseUrl, URL HTTP sin credenciales")
    public boolean isValid() {
        boolean validUrl = baseUrl == null || (baseUrl.getHost() != null && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null && baseUrl.getFragment() == null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme())));
        return validUrl
                && serviceId != null && !serviceId.isBlank()
                && connectTimeout != null && connectTimeout.isPositive()
                && readTimeout != null && readTimeout.isPositive();
    }
}
