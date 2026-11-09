package com.logitrack.shipment.cache;

import com.logitrack.shipment.client.CourierFeignClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de seguridad: verifica que la capa de caché nunca elude la autenticación.
 *
 * <p>Un cache-miss (que llega hasta {@link CourierAvailabilityLoader}) debe rechazar la
 * petición con {@link AccessDeniedException} si no hay un {@code JwtAuthenticationToken}
 * autenticado en el {@link SecurityContextHolder}.</p>
 *
 * <p>No levanta Docker. No depende de Redis.</p>
 */
@ExtendWith(MockitoExtension.class)
class CourierAvailabilitySecurityTest {

    @Mock
    private CourierFeignClient courierFeignClient;

    private CourierAvailabilityLoader loader;

    private static final UUID COURIER_ID = UUID.fromString("22222222-bbbb-cccc-dddd-000000000002");

    @BeforeEach
    void setUp() {
        loader = new CourierAvailabilityLoader(courierFeignClient);
        // Limpiar el contexto de seguridad antes de cada test.
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinAutenticacionElLoaderLanzaAccessDeniedException() {
        // Sin ningún Authentication en el contexto → AccessDeniedException antes de Feign.
        assertThatThrownBy(() -> loader.load(COURIER_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bearer");

        // El cliente Feign nunca debió ser invocado.
        verifyNoInteractions(courierFeignClient);
    }

    @Test
    void conContextoNuloElLoaderLanzaAccessDeniedException() {
        // SecurityContextHolder.getContext().getAuthentication() == null
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> loader.load(COURIER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(courierFeignClient);
    }
}
