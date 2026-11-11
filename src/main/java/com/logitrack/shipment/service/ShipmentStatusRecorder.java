package com.logitrack.shipment.service;

import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.entity.ShipmentStatusHistory;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ShipmentStatusRecorder {
    private final ShipmentStatusHistoryRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Shipment shipment, ShipmentStatus from, ShipmentStatus to, String observation) {
        repository.save(new ShipmentStatusHistory(shipment, from, to, clock.instant(), observation));
    }
}
