package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.dto.StatusHistoryResponse;
import com.logitrack.shipment.dto.PageResponse;
import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.BusinessRuleException;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.mapper.ShipmentStatusHistoryMapper;
import com.logitrack.shipment.repository.CourierRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private static final int MAX_TRACKING_ATTEMPTS = 5;
    private final ShipmentRepository shipments;
    private final ShipmentCreator creator;
    private final TrackingCodeGenerator trackingCodes;
    private final ShipmentMapper mapper;
    private final CourierRepository couriers;
    private final ShipmentStatusHistoryRepository statusHistory;
    private final ShipmentStatusHistoryMapper statusHistoryMapper;


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
    public List<StatusHistoryResponse> getHistory(UUID shipmentId) {
        if (!shipments.existsById(shipmentId)) {
            throw new ResourceNotFoundException("Envío", shipmentId);
        }
        return statusHistory.findByShipmentIdOrderByChangedAtAsc(shipmentId).stream()
                .map(statusHistoryMapper::toResponse)
                .toList();
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
