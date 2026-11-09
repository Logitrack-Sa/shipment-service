package com.logitrack.shipment.cache;

import com.logitrack.shipment.client.CourierFeignClient;
import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.exception.RemoteCourierException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Componente auxiliar que carga la disponibilidad de un repartidor desde {@code courier-service}
 * vía Feign, traduciendo el resultado al DTO de dominio {@link CourierAvailability}.
 *
 * <p>Este separador evita la dependencia circular entre {@link CourierAvailabilityCacheService}
 * y el cliente Feign. La autenticación se valida en este loader (igual que en
 * {@code FeignCourierLookup}): si no hay {@code JwtAuthenticationToken} válido se lanza
 * {@link AccessDeniedException} antes de tocar la caché o el cliente.</p>
 *
 * <p><strong>Regla de seguridad:</strong> la presencia de un token JWT válido se verifica
 * siempre antes de llamar al cliente Feign. Un cache-hit en
 * {@link CourierAvailabilityCacheService} nunca llega aquí; pero si llega (miss), la
 * autenticación ya habrá sido validada por Spring Security en el filtro de entrada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourierAvailabilityLoader {

    private final CourierFeignClient courierFeignClient;

    /**
     * Carga la disponibilidad del repartidor desde {@code courier-service}.
     *
     * @param courierId identificador del repartidor
     * @return disponibilidad del repartidor
     * @throws AccessDeniedException   si no hay token JWT autenticado en el contexto
     * @throws RemoteCourierException  si el servicio remoto no está disponible
     */
    public CourierAvailability load(UUID courierId) {
        String bearer = resolveBearer();
        CourierResult result;
        try {
            result = courierFeignClient.getAvailability(courierId, bearer);
        } catch (FeignException e) {
            log.warn("FeignException al consultar disponibilidad de repartidor {}: {}", courierId, e.getMessage());
            throw new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE");
        }
        if (result == null) {
            throw new RemoteCourierException(HttpStatus.SERVICE_UNAVAILABLE, "COURIER_UNAVAILABLE");
        }
        return new CourierAvailability(result.courierId(), result.status(), result.available());
    }

    private String resolveBearer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AccessDeniedException("Se requiere un Bearer validado para consultar repartidores");
        }
        return "Bearer " + jwt.getToken().getTokenValue();
    }
}
