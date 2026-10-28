package com.logitrack.shipment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.CourierNotAssignedException;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.InvalidStateTransitionException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.exception.ShipmentCannotBeCancelledException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ExplorarController.class)
@Import(GlobalExceptionHandlerTest.ExplorarController.class)
class GlobalExceptionHandlerTest {

    private static final UUID SHIPMENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String RUTA = "/explorar";

    @Autowired
    private MockMvc mvc;

    @Test
    void resourceNotFoundExceptionDevuelve404() throws Exception {
        mvc.perform(get(RUTA + "/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Envío no encontrado: " + SHIPMENT_ID))
                .andExpect(jsonPath("$.path").value(RUTA + "/no-encontrado"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void duplicateResourceExceptionDevuelve409() throws Exception {
        mvc.perform(get(RUTA + "/duplicado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.message").value("Tracking code ya existente: TRK-1"))
                .andExpect(jsonPath("$.path").value(RUTA + "/duplicado"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void invalidStateTransitionExceptionDevuelve422() throws Exception {
        mvc.perform(get(RUTA + "/transicion-invalida"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.message")
                        .value("Transición de estado inválida para Shipment: PENDIENTE -> ENTREGADO"))
                .andExpect(jsonPath("$.path").value(RUTA + "/transicion-invalida"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void shipmentCannotBeCancelledExceptionDevuelve422() throws Exception {
        mvc.perform(get(RUTA + "/cancelacion-invalida"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("SHIPMENT_CANNOT_BE_CANCELLED"))
                .andExpect(jsonPath("$.message").value("El envío " + SHIPMENT_ID
                        + " no puede cancelarse porque está en estado EN_TRANSITO"))
                .andExpect(jsonPath("$.path").value(RUTA + "/cancelacion-invalida"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void courierNotActiveExceptionDevuelve422() throws Exception {
        mvc.perform(get(RUTA + "/courier-inactivo"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("COURIER_NOT_ACTIVE"))
                .andExpect(jsonPath("$.message").value("El repartidor " + SHIPMENT_ID + " no está activo"))
                .andExpect(jsonPath("$.path").value(RUTA + "/courier-inactivo"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void courierNotAssignedExceptionDevuelve422() throws Exception {
        mvc.perform(get(RUTA + "/courier-sin-asignar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("COURIER_NOT_ASSIGNED"))
                .andExpect(jsonPath("$.message")
                        .value("El envío " + SHIPMENT_ID + " no tiene repartidor asignado"))
                .andExpect(jsonPath("$.path").value(RUTA + "/courier-sin-asignar"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void methodArgumentNotValidExceptionDevuelve400ConDetalles() throws Exception {
        mvc.perform(post(RUTA + "/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Revisá los campos de la solicitud"))
                .andExpect(jsonPath("$.path").value(RUTA + "/registros"))
                .andExpect(jsonPath("$.details[0].field").value("nombre"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    void constraintViolationExceptionDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/violacion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.path").value(RUTA + "/violacion"))
                .andExpect(jsonPath("$.details[0].field").value("min"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    void parametroDeMetodoValidadoDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/restriccion").param("min", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.path").value(RUTA + "/restriccion"))
                .andExpect(jsonPath("$.details[0].field").value("restriccion.min"))
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    void httpMessageNotReadableExceptionDevuelve400() throws Exception {
        mvc.perform(post(RUTA + "/registros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{json malformado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es un JSON válido"))
                .andExpect(jsonPath("$.path").value(RUTA + "/registros"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void methodArgumentTypeMismatchExceptionDevuelve400() throws Exception {
        mvc.perform(get(RUTA + "/uuid/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("El parámetro 'id' tiene un tipo inválido"))
                .andExpect(jsonPath("$.path").value(RUTA + "/uuid/no-es-uuid"))
                .andExpect(jsonPath("$.details[0].field").value("id"))
                .andExpect(jsonPath("$.details[0].message").value("Valor inválido: no-es-uuid"));
    }

    @Test
    void optimisticLockingDevuelve409() throws Exception {
        mvc.perform(get(RUTA + "/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.message").value("La entidad fue modificada por otra operación, reintentá"))
                .andExpect(jsonPath("$.path").value(RUTA + "/optimistic-lock"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void exceptionGenericaDevuelve500SinExponerDetallesInternos() throws Exception {
        mvc.perform(get(RUTA + "/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado, intentá nuevamente"))
                .andExpect(jsonPath("$.message", not(containsString("confidencial"))))
                .andExpect(jsonPath("$.path").value(RUTA + "/boom"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @RestController
    @Validated
    static class ExplorarController {

        private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

        @GetMapping("/explorar/no-encontrado")
        void noEncontrado() {
            throw new ResourceNotFoundException("Envío", SHIPMENT_ID);
        }

        @GetMapping("/explorar/duplicado")
        void duplicado() {
            throw new DuplicateResourceException("Tracking code ya existente: TRK-1");
        }

        @GetMapping("/explorar/transicion-invalida")
        void transicionInvalida() {
            throw new InvalidStateTransitionException("Shipment", "PENDIENTE", "ENTREGADO");
        }

        @GetMapping("/explorar/cancelacion-invalida")
        void cancelacionInvalida() {
            throw new ShipmentCannotBeCancelledException(SHIPMENT_ID, "EN_TRANSITO");
        }

        @GetMapping("/explorar/courier-inactivo")
        void courierInactivo() {
            throw new CourierNotActiveException(SHIPMENT_ID);
        }

        @GetMapping("/explorar/courier-sin-asignar")
        void courierSinAsignar() {
            throw new CourierNotAssignedException(SHIPMENT_ID);
        }

        @GetMapping("/explorar/boom")
        void boom() {
            throw new IllegalStateException("detalle interno confidencial");
        }

        @PostMapping("/explorar/registros")
        void registro(@Valid @RequestBody RegistroRequest request) {
        }

        @GetMapping("/explorar/uuid/{id}")
        void porUuid(@PathVariable UUID id) {
        }

        @GetMapping("/explorar/violacion")
        void violacion() {
            Set<ConstraintViolation<RestriccionConMinima>> violations =
                    VALIDATOR.validateProperty(new RestriccionConMinima(), "min");
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/explorar/restriccion")
        void restriccion(@RequestParam @Min(1) int min) {
        }

        @GetMapping("/explorar/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Shipment", SHIPMENT_ID);
        }
    }

    static class RestriccionConMinima {

        @Min(1)
        int min = 0;
    }

    static class RegistroRequest {

        @NotBlank
        private String nombre;

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }
    }
}
