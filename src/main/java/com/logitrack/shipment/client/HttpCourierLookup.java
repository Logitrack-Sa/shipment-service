package com.logitrack.shipment.client;

import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.RemoteCourierException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HttpCourierLookup implements CourierLookup {
    private final RestClient courierRestClient;

    public void requireAvailable(UUID id) {
        var result = fetch("/api/v1/couriers/{id}/availability", id);
        validateIdentity(result, id);
        if (result.available() == null || result.available() != "ACTIVO".equals(result.status())) {
            throw unavailable();
        }
        if (!result.available()) throw new CourierNotActiveException(id);
    }

    public void requireActive(UUID id) {
        var result = fetch("/api/v1/internal/couriers/{id}/status", id);
        validateIdentity(result, id);
        if (!"ACTIVO".equals(result.status())) throw new CourierNotActiveException(id);
    }

    private CourierResult fetch(String path, UUID id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
        }
        try {
            return courierRestClient.get().uri(path, id)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve().onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        switch (response.getStatusCode().value()) {
                            case 404 -> throw new ResourceNotFoundException("Repartidor", id);
                            case 422 -> throw new CourierNotActiveException(id);
                            case 401 -> throw new RemoteCourierException(HttpStatus.UNAUTHORIZED, "COURIER_UNAUTHORIZED");
                            case 403 -> throw new RemoteCourierException(HttpStatus.FORBIDDEN, "COURIER_FORBIDDEN");
                            default -> throw unavailable();
                        }
                    }).body(CourierResult.class);
        } catch (RestClientException exception) {
            throw unavailable();
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

    public record CourierResult(UUID courierId, String status, Boolean available) { }
}
