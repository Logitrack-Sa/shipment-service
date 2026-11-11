package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShipmentCreator {
    private final CustomerRepository customers;
    private final ShipmentRepository shipments;
    private final FeeCalculator fees;
    private final ShipmentMapper mapper;
    private final ShipmentStatusRecorder recorder;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ShipmentResponse create(CreateShipmentRequest request, String trackingCode) {
        var customer = customers.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", request.customerId()));
        var fee = fees.calculate(request.weightKg(), request.widthCm(), request.heightCm(), request.lengthCm());
        var shipment = shipments.saveAndFlush(mapper.toEntity(request, customer, trackingCode, fee));
        recorder.record(shipment, null, ShipmentStatus.PENDIENTE, "Alta del envío");
        return mapper.toResponse(shipment);
    }
}
