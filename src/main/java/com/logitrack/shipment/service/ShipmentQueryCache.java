package com.logitrack.shipment.service;

import com.logitrack.shipment.config.CacheConfig;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Componente interno responsable de consultar la base de datos y cachear
 * las respuestas de envíos por identificador y código de seguimiento.
 *
 * <p>Separado de {@link ShipmentService} para que las invocaciones atraviesen
 * los proxies de Spring (@Cacheable) sin self-invocation, y para que la capa
 * de seguridad y autorización se ejecute siempre en la fachada sin cachear
 * decisiones de acceso.</p>
 */
@Service
@RequiredArgsConstructor
public class ShipmentQueryCache {

    private final ShipmentRepository shipments;
    private final ShipmentMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.SHIPMENT_CACHE_NAME, key = "'shipment::id::' + #id")
    public ShipmentResponse findById(UUID id) {
        Shipment shipment = shipments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", id));
        return mapper.toResponse(shipment);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.SHIPMENT_CACHE_NAME, key = "'shipment::tracking::' + #trackingCode")
    public ShipmentResponse findByTrackingCode(String trackingCode) {
        Shipment shipment = shipments.findByTrackingCode(trackingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", trackingCode));
        return mapper.toResponse(shipment);
    }
}
