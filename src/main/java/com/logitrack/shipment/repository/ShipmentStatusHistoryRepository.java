package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.ShipmentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentStatusHistoryRepository extends JpaRepository<ShipmentStatusHistory, UUID> {
    List<ShipmentStatusHistory> findByShipmentIdOrderByChangedAtAsc(UUID shipmentId);
}
