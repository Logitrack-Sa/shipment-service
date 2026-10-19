package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CourierRequest;
import com.logitrack.shipment.dto.CourierResponse;
import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.CourierStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.CourierMapper;
import com.logitrack.shipment.repository.CourierRepository;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CourierService {

    private static final Logger log = LoggerFactory.getLogger(CourierService.class);

    private final CourierRepository courierRepository;
    private final CourierMapper courierMapper;

    @Transactional
    public CourierResponse create(CourierRequest request) {
        Courier saved = courierRepository.save(courierMapper.toEntity(request));
        log.info("Repartidor registrado id={}", saved.getId());
        return courierMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CourierResponse findById(UUID id) {
        log.debug("Buscando repartidor id={}", id);
        return courierMapper.toResponse(findEntity(id));
    }

    /**
     * Lista repartidores. Si {@code status} es nulo, devuelve todos.
     */
    @Transactional(readOnly = true)
    public Page<CourierResponse> findAll(CourierStatus status, Pageable pageable) {
        log.debug("Listando repartidores status={} page={} size={}",
            status, pageable.getPageNumber(), pageable.getPageSize());

        Page<Courier> page = (status == null)
            ? courierRepository.findAll(pageable)
            : courierRepository.findByStatus(status, pageable);

        return page.map(courierMapper::toResponse);
    }

    /**
     * Activa o desactiva un repartidor. Como la entidad está gestionada por la
     * transacción, el cambio se persiste al hacer commit (dirty checking).
     */
    @Transactional
    public CourierResponse changeStatus(UUID id, CourierStatus newStatus) {
        Courier courier = findEntity(id);
        CourierStatus previous = courier.getStatus();

        courier.changeStatus(newStatus);
        log.info("Estado de repartidor actualizado id={} {} -> {}", id, previous, newStatus);

        return courierMapper.toResponse(courier);
    }

    private Courier findEntity(UUID id) {
        return courierRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Repartidor no encontrado id={}", id);
                return new ResourceNotFoundException("Repartidor", id);            });
    }
}
