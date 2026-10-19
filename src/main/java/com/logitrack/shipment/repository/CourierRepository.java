package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.CourierStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    Page<Courier> findByStatus(CourierStatus status, Pageable pageable);
}
