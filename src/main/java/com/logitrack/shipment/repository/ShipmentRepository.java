package com.logitrack.shipment.repository;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {
    @Override
    @EntityGraph(attributePaths = {"customer"})
    Optional<Shipment> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Shipment> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"customer"})
    Optional<Shipment> findByTrackingCode(String trackingCode);

    @EntityGraph(attributePaths = {"customer"})
    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"customer"})
    Page<Shipment> findAll(Specification<Shipment> spec, Pageable pageable);

    boolean existsByTrackingCode(String trackingCode);
}
