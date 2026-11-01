package com.logitrack.shipment.client;

import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CourierFeignFallbackFactory implements FallbackFactory<CourierFeignClient> {

    @Override
    public CourierFeignClient create(Throwable cause) {
        return new CourierFeignClient() {
            @Override
            public CourierResult getAvailability(UUID id) {
                throw handleFailure("getAvailability", id, cause);
            }

            @Override
            public CourierResult getStatus(UUID id) {
                throw handleFailure("getStatus", id, cause);
            }
        };
    }

    RuntimeException handleFailure(String operation, UUID courierId, Throwable cause) {
        RuntimeException known = unwrapKnownException(cause);
        if (known != null) {
            return known;
        }
        String reason = cause != null ? cause.getClass().getSimpleName() : "UnknownError";
        log.warn("Fallo de comunicación con courier-service en {} para repartidor {} (causa: {})",
                operation, courierId, reason);
        return new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE",
                "No se pudo validar el repartidor en el servicio remoto");
    }

    private static RuntimeException unwrapKnownException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ResourceNotFoundException
                    || current instanceof CourierNotActiveException
                    || current instanceof RemoteCourierException
                    || current instanceof AccessDeniedException) {
                return (RuntimeException) current;
            }
            current = current.getCause();
        }
        return null;
    }
}
