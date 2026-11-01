package com.logitrack.shipment.client;

import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class CourierErrorDecoder implements ErrorDecoder {
    private static final Pattern UUID_PATTERN =
            Pattern.compile("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response != null ? response.status() : 503;
        UUID courierId = extractCourierId(response);
        log.warn("Respuesta HTTP {} desde courier-service en {} para repartidor {}",
                status, methodKey, courierId != null ? courierId : "desconocido");

        return switch (status) {
            case 404 -> courierId != null
                    ? new ResourceNotFoundException("Repartidor", courierId)
                    : new ResourceNotFoundException("Repartidor no encontrado");
            case 422 -> courierId != null
                    ? new CourierNotActiveException(courierId)
                    : new CourierNotActiveException("El repartidor no está activo");
            case 401 -> new RemoteCourierException(HttpStatus.UNAUTHORIZED, "COURIER_UNAUTHORIZED",
                    "Autenticación rechazada por courier-service");
            case 403 -> new RemoteCourierException(HttpStatus.FORBIDDEN, "COURIER_FORBIDDEN",
                    "Acceso denegado por courier-service");
            default -> new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE",
                    "No se pudo validar el repartidor en el servicio remoto");
        };
    }

    static UUID extractCourierId(Response response) {
        if (response == null || response.request() == null || response.request().url() == null) {
            return null;
        }
        Matcher matcher = UUID_PATTERN.matcher(response.request().url());
        if (matcher.find()) {
            return UUID.fromString(matcher.group(1));
        }
        return null;
    }
}
