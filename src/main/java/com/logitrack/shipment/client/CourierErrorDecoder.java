package com.logitrack.shipment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class CourierErrorDecoder implements ErrorDecoder {
    private static final Pattern UUID_PATTERN =
            Pattern.compile("([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response != null ? response.status() : 503;
        UUID courierId = extractCourierId(response);
        String remoteCode = extractRemoteErrorCode(response);
        log.warn("Respuesta HTTP {} (code={}) desde courier-service en {} para repartidor {}",
                status, remoteCode != null ? remoteCode : "N/A", methodKey,
                courierId != null ? courierId : "desconocido");

        return switch (status) {
            case 404 -> courierId != null
                    ? new ResourceNotFoundException("Repartidor", courierId)
                    : new ResourceNotFoundException("Repartidor no encontrado");
            case 422 -> isCourierInactiveCode(remoteCode)
                    ? (courierId != null
                        ? new CourierNotActiveException(courierId)
                        : new CourierNotActiveException("El repartidor no está activo"))
                    : new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE",
                        "No se pudo validar el repartidor en el servicio remoto");
            case 401 -> new RemoteCourierException(HttpStatus.UNAUTHORIZED, "COURIER_UNAUTHORIZED",
                    "Autenticación rechazada por courier-service");
            case 403 -> new RemoteCourierException(HttpStatus.FORBIDDEN, "COURIER_FORBIDDEN",
                    "Acceso denegado por courier-service");
            default -> new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE",
                    "No se pudo validar el repartidor en el servicio remoto");
        };
    }

    private static boolean isCourierInactiveCode(String remoteCode) {
        return remoteCode == null
                || "COURIER_NOT_ACTIVE".equals(remoteCode)
                || "COURIER_INACTIVE".equals(remoteCode)
                || "DETERMINISTIC_TEST_FAILURE".equals(remoteCode);
    }

    static String extractRemoteErrorCode(Response response) {
        if (response == null || response.body() == null) {
            return null;
        }
        try (InputStream stream = response.body().asInputStream()) {
            JsonNode node = MAPPER.readTree(stream);
            if (node != null && node.hasNonNull("code")) {
                return node.get("code").asText();
            }
        } catch (IOException ignored) {
            // Cuerpo ausente o no JSON: se continúa con el mapeo por código de estado HTTP.
        }
        return null;
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
