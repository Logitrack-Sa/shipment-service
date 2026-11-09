package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.PublicTrackingResponse;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicTrackingService {
    private final ShipmentRepository shipments;
    private final ShipmentStatusHistoryRepository history;

    @Transactional(readOnly = true)
    public PublicTrackingResponse find(String code) {
        var shipment = shipments.findByTrackingCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", code));
        var movements = history.findByShipmentIdOrderByChangedAtAsc(shipment.getId()).stream()
                .map(item -> new PublicTrackingResponse.Movement(item.getNewStatus(), item.getChangedAt()))
                .toList();
        return new PublicTrackingResponse(shipment.getTrackingCode(), shipment.getStatus(),
                shipment.getCreatedAt(), movements);
    }
}
