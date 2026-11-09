package com.logitrack.shipment.service;

import com.logitrack.shipment.config.RabbitMQConfig;
import com.logitrack.shipment.config.RedisCacheConfig;
import com.logitrack.shipment.security.ShipmentAccess;
import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.dto.ShipmentStatusChangedEvent;
import com.logitrack.shipment.dto.PageResponse;
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
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.repository.ShipmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class ShipmentService {
    private static final int MAX_TRACKING_ATTEMPTS = 5;
    /** Observación por defecto registrada en el historial al cancelar un envío. */
    private static final String CANCELLATION_OBSERVATION = "Cancelación del envío";
    private final ShipmentRepository shipments;
    private final ShipmentCreator creator;
    private final TrackingCodeGenerator trackingCodes;
    private final ShipmentMapper mapper;
    private final CourierLookup couriers;
    private final ShipmentStatusRecorder statusRecorder;
    private final ShipmentAccess access;
    private final RabbitTemplate rabbitTemplate;

    public ShipmentService(
            ShipmentRepository shipments,
            ShipmentCreator creator,
            TrackingCodeGenerator trackingCodes,
            ShipmentMapper mapper,
            CourierLookup couriers,
            ShipmentStatusRecorder statusRecorder,
            ShipmentAccess access) {
        this(shipments, creator, trackingCodes, mapper, couriers, statusRecorder, access, null);
    }

    @Autowired
    public ShipmentService(
            ShipmentRepository shipments,
            ShipmentCreator creator,
            TrackingCodeGenerator trackingCodes,
            ShipmentMapper mapper,
            CourierLookup couriers,
            ShipmentStatusRecorder statusRecorder,
            ShipmentAccess access,
            RabbitTemplate rabbitTemplate) {
        this.shipments = shipments;
        this.creator = creator;
        this.trackingCodes = trackingCodes;
        this.mapper = mapper;
        this.couriers = couriers;
        this.statusRecorder = statusRecorder;
        this.access = access;
        this.rabbitTemplate = rabbitTemplate;
    }


    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::tracking::' + #result.trackingCode"),
        @CacheEvict(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::id::' + #result.id")
    })
    public ShipmentResponse assignCourier(UUID shipmentId, UUID courierId) {
        access.requireAdmin();
        Shipment shipment = shipments.findById(shipmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Envío", shipmentId));
        couriers.requireAvailable(courierId);
        if (shipment.getStatus() != ShipmentStatus.PENDIENTE) {
            throw new BusinessRuleException("Solo se puede asignar un repartidor a un envío pendiente");
        }
        shipment.assignCourier(courierId);
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
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::tracking::' + #result.trackingCode"),
        @CacheEvict(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::id::' + #result.id")
    })
    public ShipmentResponse changeStatus(UUID shipmentId, ShipmentStatus target, String observation) {
        Shipment shipment = findShipment(shipmentId);
        access.requireTransition(shipment, target);
        return transition(shipment, target, observation);
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
    @Caching(evict = {
        @CacheEvict(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::tracking::' + #result.trackingCode"),
        @CacheEvict(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::id::' + #result.id")
    })
    public ShipmentResponse cancel(UUID shipmentId) {
        access.requireAdmin();
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
            if (shipment.getCourierId() == null) {
                throw new CourierNotAssignedException(shipment.getId());
            }
            couriers.requireActive(shipment.getCourierId());
        }

        shipment.changeStatusTo(target);
        statusRecorder.record(shipment, from, target, observation);
        publishStatusChangedEvent(shipment, from, target);
        return mapper.toResponse(shipment);
    }

    private void publishStatusChangedEvent(Shipment shipment, ShipmentStatus from, ShipmentStatus target) {
        if (rabbitTemplate == null) {
            return;
        }
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                UUID.randomUUID(),
                shipment.getId() != null ? shipment.getId().toString() : null,
                shipment.getTrackingCode(),
                shipment.getCourierId() != null ? shipment.getCourierId().toString() : null,
                from,
                target,
                Instant.now()
        );
        Runnable sendEvent = () -> {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.SHIPMENT_EXCHANGE,
                        RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY,
                        event
                );
            } catch (AmqpException exception) {
                log.warn("No se pudo publicar el evento ShipmentStatusChangedEvent en RabbitMQ para el envío {}: {}",
                        shipment.getId(), exception.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendEvent.run();
                }
            });
        } else {
            sendEvent.run();
        }
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
    @Cacheable(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::id::' + #id")
    public ShipmentResponse findById(UUID id) {
        Shipment shipment = findShipment(id);
        access.requireRead(shipment);
        return mapper.toResponse(shipment);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisCacheConfig.SHIPMENT_QUERIES_CACHE, key = "'shipment::tracking::' + #trackingCode")
    public ShipmentResponse findByTrackingCode(String trackingCode) {
        Shipment shipment = shipments.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", trackingCode));
        access.requireRead(shipment);
        return mapper.toResponse(shipment);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShipmentResponse> findAll(ShipmentStatus status, Pageable pageable) {
        var page = shipments.findAll(access.scope(status), pageable);
        return PageResponse.from(page.map(mapper::toResponse));
    }

    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        access.requireCreate(request.customerId());
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
