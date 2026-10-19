package com.logitrack.shipment.mapper;

import com.logitrack.shipment.dto.StatusHistoryResponse;
import com.logitrack.shipment.entity.ShipmentStatusHistory;
import org.springframework.stereotype.Component;

@Component
public class ShipmentStatusHistoryMapper {
    public StatusHistoryResponse toResponse(ShipmentStatusHistory history) {
        return new StatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangedAt(),
                history.getObservation());
    }
}
