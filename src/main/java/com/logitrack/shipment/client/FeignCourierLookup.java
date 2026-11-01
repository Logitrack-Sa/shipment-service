package com.logitrack.shipment.client;

import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class FeignCourierLookup implements CourierLookup {
    private final CourierFeignClient courierFeignClient;

    @Override
    public void requireAvailable(UUID id) {
        var result = execute(id, () -> courierFeignClient.getAvailability(id));
        validateIdentity(result, id);
        if (result.available() == null || result.available() != "ACTIVO".equals(result.status())) {
            throw unavailable();
        }
        if (!result.available()) {
            throw new CourierNotActiveException(id);
        }
    }

    @Override
    public void requireActive(UUID id) {
        var result = execute(id, () -> courierFeignClient.getStatus(id));
        validateIdentity(result, id);
        if (!"ACTIVO".equals(result.status())) {
            throw new CourierNotActiveException(id);
        }
    }

    private CourierResult execute(UUID id, Supplier<CourierResult> call) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
        }
        try {
            return call.get();
        } catch (FeignException exception) {
            throw switch (exception.status()) {
                case 404 -> new ResourceNotFoundException("Repartidor", id);
                case 422 -> new CourierNotActiveException(id);
                case 401 -> new RemoteCourierException(HttpStatus.UNAUTHORIZED, "COURIER_UNAUTHORIZED");
                case 403 -> new RemoteCourierException(HttpStatus.FORBIDDEN, "COURIER_FORBIDDEN");
                default -> unavailable();
            };
        }
    }

    private void validateIdentity(CourierResult result, UUID id) {
        if (result == null || !id.equals(result.courierId())
                || !("ACTIVO".equals(result.status()) || "INACTIVO".equals(result.status()))) {
            throw unavailable();
        }
    }

    private static RemoteCourierException unavailable() {
        return new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE");
    }
}
