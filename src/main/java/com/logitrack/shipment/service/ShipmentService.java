package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private static final int MAX_TRACKING_ATTEMPTS = 5;
    private final ShipmentRepository shipments;
    private final ShipmentCreator creator;
    private final TrackingCodeGenerator trackingCodes;

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
