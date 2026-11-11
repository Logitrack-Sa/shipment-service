package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    @Override
    @EntityGraph(attributePaths = {"customer", "courier"})
    Optional<Shipment> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"customer", "courier"})
    Page<Shipment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "courier"})
    Optional<Shipment> findByTrackingCode(String trackingCode);

    @EntityGraph(attributePaths = {"customer", "courier"})
    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    boolean existsByTrackingCode(String trackingCode);
}
