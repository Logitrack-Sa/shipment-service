package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.ShipmentHistoryResponse;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentHistoryService {
    private final ShipmentService shipments;
    private final ShipmentStatusHistoryRepository history;

    @Transactional(readOnly = true)
    public List<ShipmentHistoryResponse> find(UUID shipmentId) {
        shipments.findById(shipmentId); // Misma autorización para UUID, tracking e historial.
        return history.findByShipmentIdOrderByChangedAtAsc(shipmentId).stream()
                .map(item -> new ShipmentHistoryResponse(item.getId(), item.getPreviousStatus(),
                        item.getNewStatus(), item.getChangedAt(), item.getObservation())).toList();
    }
}
