package com.logitrack.shipment.client;

import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import feign.FeignException;
import java.util.UUID;
import java.util.function.BiFunction;
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
        CourierResult result = execute((client, bearer) -> client.getAvailability(id, bearer));
        CourierResult validated = validateIdentity(result, id);
        Boolean available = validated.available();
        if (available == null || available != "ACTIVO".equals(validated.status())) {
            log.warn("Payload inconsistente de disponibilidad recibido desde courier-service para repartidor {}", id);
            throw unavailable();
        }
        if (!available) {
            throw new CourierNotActiveException(id);
        }
    }

    @Override
    public void requireActive(UUID id) {
        CourierResult result = execute((client, bearer) -> client.getStatus(id, bearer));
        CourierResult validated = validateIdentity(result, id);
        if (!"ACTIVO".equals(validated.status())) {
            throw new CourierNotActiveException(id);
        }
    }

    private CourierResult execute(BiFunction<CourierFeignClient, String, CourierResult> call) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
        }
        String bearer = "Bearer " + jwt.getToken().getTokenValue();
        try {
            CourierResult response = call.apply(courierFeignClient, bearer);
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (FeignException exception) {
            return call.apply(fallbackFactory.create(exception), bearer);
        }
    }

    private CourierResult validateIdentity(CourierResult result, UUID id) {
        if (result == null
                || result.courierId() == null
                || result.status() == null
                || !id.equals(result.courierId())
                || !("ACTIVO".equals(result.status()) || "INACTIVO".equals(result.status()))) {
            log.warn("Respuesta inválida o identidad no coincidente desde courier-service para repartidor {}", id);
            throw unavailable();
        }
        return result;
    }

    private static RemoteCourierException unavailable() {
        return new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE");
    }
}
