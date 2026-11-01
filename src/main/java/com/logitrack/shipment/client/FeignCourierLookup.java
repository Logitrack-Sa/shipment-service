package com.logitrack.shipment.client;

import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import feign.FeignException;
import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeignCourierLookup implements CourierLookup {
    private final CourierFeignClient courierFeignClient;
    private final CourierFeignFallbackFactory fallbackFactory;

    public FeignCourierLookup(CourierFeignClient courierFeignClient) {
        this(courierFeignClient, new CourierFeignFallbackFactory());
    }

    @Autowired
    public FeignCourierLookup(CourierFeignClient courierFeignClient, CourierFeignFallbackFactory fallbackFactory) {
        this.courierFeignClient = courierFeignClient;
        this.fallbackFactory = fallbackFactory;
    }

    @Override
    public void requireAvailable(UUID id) {
        var result = execute(id, client -> client.getAvailability(id));
        validateIdentity(result, id);
        if (result.available() == null || result.available() != "ACTIVO".equals(result.status())) {
            log.warn("Payload inconsistente de disponibilidad recibido desde courier-service para repartidor {}", id);
            throw unavailable();
        }
        if (!result.available()) {
            throw new CourierNotActiveException(id);
        }
    }

    @Override
    public void requireActive(UUID id) {
        var result = execute(id, client -> client.getStatus(id));
        validateIdentity(result, id);
        if (!"ACTIVO".equals(result.status())) {
            throw new CourierNotActiveException(id);
        }
    }

    private CourierResult execute(UUID id, Function<CourierFeignClient, CourierResult> call) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
        }
        try {
            return call.apply(courierFeignClient);
        } catch (FeignException exception) {
            return call.apply(fallbackFactory.create(exception));
        }
    }

    private void validateIdentity(CourierResult result, UUID id) {
        if (result == null || !id.equals(result.courierId())
                || !("ACTIVO".equals(result.status()) || "INACTIVO".equals(result.status()))) {
            log.warn("Respuesta inválida o identidad no coincidente desde courier-service para repartidor {}", id);
            throw unavailable();
        }
    }

    private static RemoteCourierException unavailable() {
        return new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE");
    }
}
