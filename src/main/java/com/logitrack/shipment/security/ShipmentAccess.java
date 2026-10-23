package com.logitrack.shipment.security;

import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ShipmentAccess {
    public void requireAdmin() { if (!hasRole("ADMIN")) throw denied(); }

    public void requireCreate(UUID customerId) {
        if (!hasRole("ADMIN") && !(hasRole("CLIENTE") && customerId.equals(link("customerId")))) throw denied();
    }

    public void requireRead(Shipment shipment) {
        if (!hasRole("ADMIN")
                && !(hasRole("CLIENTE") && shipment.getCustomer().getId().equals(link("customerId")))
                && !(hasRole("REPARTIDOR") && shipment.getCourierId() != null
                    && shipment.getCourierId().equals(link("courierId")))) throw denied();
    }

    public void requireTransition(Shipment shipment, ShipmentStatus target) {
        if (hasRole("ADMIN")) return;
        if (!hasRole("REPARTIDOR") || shipment.getCourierId() == null
                || !shipment.getCourierId().equals(link("courierId"))
                || (target != ShipmentStatus.EN_RUTA && target != ShipmentStatus.ENTREGADO)) throw denied();
    }

    public Specification<Shipment> scope(ShipmentStatus status) {
        boolean admin = hasRole("ADMIN");
        UUID customerId = hasRole("CLIENTE") ? link("customerId") : null;
        UUID courierId = hasRole("REPARTIDOR") ? link("courierId") : null;
        if (!admin && customerId == null && courierId == null) throw denied();
        return (root, query, cb) -> {
            var owned = admin ? cb.conjunction() : cb.or(
                    customerId == null ? cb.disjunction() : cb.equal(root.get("customer").get("id"), customerId),
                    courierId == null ? cb.disjunction() : cb.equal(root.get("courierId"), courierId));
            return status == null ? owned : cb.and(owned, cb.equal(root.get("status"), status));
        };
    }

    private boolean hasRole(String role) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()) throw denied();
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private UUID link(String claim) {
        try {
            Jwt jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
            return UUID.fromString(jwt.getClaimAsString(claim));
        } catch (RuntimeException exception) { throw denied(); }
    }

    private static AccessDeniedException denied() { return new AccessDeniedException("Acceso a envío denegado"); }
}
