package com.logitrack.shipment.client;

import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import feign.FeignException;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeignCourierLookup implements CourierLookup {
    private final CourierFeignClient courierFeignClient;

    @Override
    public void requireAvailable(UUID id) {
        CourierResult result = execute(id, () -> courierFeignClient.getAvailability(id));
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
        CourierResult result = execute(id, () -> courierFeignClient.getStatus(id));
        CourierResult validated = validateIdentity(result, id);
        if (!"ACTIVO".equals(validated.status())) {
            throw new CourierNotActiveException(id);
        }
    }

    private CourierResult execute(UUID id, Supplier<CourierResult> call) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
        }
        try {
            CourierResult response = call.get();
            if (response == null) {
                throw unavailable();
            }
            return response;
        } catch (FeignException exception) {
            log.warn("Fallo HTTP/transporte ({}) al consultar repartidor {} en courier-service",
                    exception.status(), id);
            throw switch (exception.status()) {
                case 404 -> new ResourceNotFoundException("Repartidor", id);
                case 422 -> new CourierNotActiveException(id);
                case 401 -> new RemoteCourierException(HttpStatus.UNAUTHORIZED, "COURIER_UNAUTHORIZED");
                case 403 -> new RemoteCourierException(HttpStatus.FORBIDDEN, "COURIER_FORBIDDEN");
                default -> unavailable();
            };
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
