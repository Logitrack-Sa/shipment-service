package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.dto.PageResponse;
import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.BusinessRuleException;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.CourierNotAssignedException;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.InvalidStateTransitionException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.exception.ShipmentCannotBeCancelledException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CourierRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private static final int MAX_TRACKING_ATTEMPTS = 5;
    /** Observación por defecto registrada en el historial al cancelar un envío. */
    private static final String CANCELLATION_OBSERVATION = "Cancelación del envío";
    private final ShipmentRepository shipments;
    private final ShipmentCreator creator;
    private final TrackingCodeGenerator trackingCodes;
    private final ShipmentMapper mapper;
    private final CourierRepository couriers;
    private final ShipmentStatusRecorder statusRecorder;


    @Transactional
    public ShipmentResponse assignCourier(UUID shipmentId, UUID courierId) {
        Shipment shipment = shipments.findById(shipmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Envío", shipmentId));
        Courier courier = couriers.findById(courierId)
            .orElseThrow(() -> new ResourceNotFoundException("Repartidor", courierId));
        if (!courier.isAvailable()) {
            throw new CourierNotActiveException("El repartidor no está activo: " + courierId);
        }
        if (shipment.getStatus() != ShipmentStatus.PENDIENTE) {
            throw new BusinessRuleException("Solo se puede asignar un repartidor a un envío pendiente");
        }
        shipment.assignCourier(courier);
        return mapper.toResponse(shipment);
    }

    /**
     * Cambia el estado de un envío aplicando la máquina de estados del dominio.
     *
     * <p>Valida las reglas de repartidor cuando el destino es {@link ShipmentStatus#EN_RUTA}
     * (debe existir un repartidor asignado y estar activo) y registra el cambio en el
     * historial de estados mediante {@link ShipmentStatusRecorder}.</p>
     *
     * @param shipmentId  identificador del envío a modificar
     * @param target      estado destino solicitado
     * @param observation observación opcional que se guarda en el historial
     * @return el envío actualizado
     * @throws ResourceNotFoundException       si el envío no existe
     * @throws InvalidStateTransitionException si la transición no está permitida
     * @throws CourierNotAssignedException     si pasa a {@code EN_RUTA} sin repartidor asignado
     * @throws CourierNotActiveException       si pasa a {@code EN_RUTA} con un repartidor inactivo
     */
    @Transactional
    public ShipmentResponse changeStatus(UUID shipmentId, ShipmentStatus target, String observation) {
        return transition(findShipment(shipmentId), target, observation);
    }

    /**
     * Cancela un envío delegando en la misma transición que {@link #changeStatus}.
     *
     * <p>Solo se permite cancelar envíos en estado {@link ShipmentStatus#PENDIENTE}. La
     * cancelación no elimina el envío, conserva su repartidor asignado (si lo tuviera) y
     * registra el cambio a {@link ShipmentStatus#CANCELADO} en el historial.</p>
     *
     * @param shipmentId identificador del envío a cancelar
     * @return el envío cancelado
     * @throws ResourceNotFoundException          si el envío no existe
     * @throws ShipmentCannotBeCancelledException si el envío no está en estado {@code PENDIENTE}
     */
    @Transactional
    public ShipmentResponse cancel(UUID shipmentId) {
        Shipment shipment = findShipment(shipmentId);
        if (shipment.getStatus() != ShipmentStatus.PENDIENTE) {
            throw new ShipmentCannotBeCancelledException(shipmentId, shipment.getStatus().name());
        }
        return transition(shipment, ShipmentStatus.CANCELADO, CANCELLATION_OBSERVATION);
    }

    /**
     * Aplica una transición de estado sobre un envío ya cargado, dejando un único camino de
     * código para los cambios de estado y la cancelación.
     *
     * @param shipment    envío a modificar
     * @param target      estado destino solicitado
     * @param observation observación opcional que se guarda en el historial
     * @return el envío actualizado
     * @throws InvalidStateTransitionException si la transición no está permitida
     * @throws CourierNotAssignedException     si pasa a {@code EN_RUTA} sin repartidor asignado
     * @throws CourierNotActiveException       si pasa a {@code EN_RUTA} con un repartidor inactivo
     */
    private ShipmentResponse transition(Shipment shipment, ShipmentStatus target, String observation) {
        ShipmentStatus from = shipment.getStatus();

        if (!from.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Envío", from.name(), String.valueOf(target));
        }

        if (target == ShipmentStatus.EN_RUTA) {
            Courier courier = shipment.getCourier();
            if (courier == null) {
                throw new CourierNotAssignedException(shipment.getId());
            }
            if (!courier.isAvailable()) {
                throw new CourierNotActiveException(courier.getId());
            }
        }

        shipment.changeStatusTo(target);
        statusRecorder.record(shipment, from, target, observation);
        return mapper.toResponse(shipment);
    }

    /**
     * Busca un envío por su identificador.
     *
     * @param shipmentId identificador del envío
     * @return el envío encontrado
     * @throws ResourceNotFoundException si el envío no existe
     */
    private Shipment findShipment(UUID shipmentId) {
        return shipments.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", shipmentId));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findById(UUID id) {
        return shipments.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", id));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findByTrackingCode(String trackingCode) {
        return shipments.findByTrackingCode(trackingCode).map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", trackingCode));
    }

    @Transactional(readOnly = true)
    public PageResponse<ShipmentResponse> findAll(ShipmentStatus status, Pageable pageable) {
        var page = status == null ? shipments.findAll(pageable) : shipments.findByStatus(status, pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        for (int attempt = 0; attempt < MAX_TRACKING_ATTEMPTS; attempt++) {
            String code = trackingCodes.generate();
            if (shipments.existsByTrackingCode(code)) continue;
            try {
                return creator.create(request, code);
            } catch (DataIntegrityViolationException exception) {
                if (!shipments.existsByTrackingCode(code)) throw exception;
            }
        }
        throw new DuplicateResourceException("No se pudo generar un código de seguimiento único");
    }
}
