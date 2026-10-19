package com.logitrack.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.logitrack.shipment.dto.CourierRequest;
import com.logitrack.shipment.dto.CourierResponse;
import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.CourierStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.CourierMapper;
import com.logitrack.shipment.repository.CourierRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    @Mock
    private CourierRepository courierRepository;

    @Spy
    private CourierMapper courierMapper = new CourierMapper();

    @InjectMocks
    private CourierService courierService;

    @Test
    void createReturnsCourierActiveByDefault() {
        when(courierRepository.save(any(Courier.class))).thenAnswer(inv -> inv.getArgument(0));

        CourierResponse response = courierService.create(new CourierRequest("Luis", "MOTO"));

        assertThat(response.name()).isEqualTo("Luis");
        assertThat(response.vehicleType()).isEqualTo("MOTO");
        assertThat(response.status()).isEqualTo(CourierStatus.ACTIVO);
    }

    @Test
    void findByIdThrowsWhenCourierDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(courierRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.findById(id))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findAllWithoutStatusReturnsEveryCourier() {
        Pageable pageable = Pageable.ofSize(20);
        when(courierRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        courierService.findAll(null, pageable);

        verify(courierRepository, never()).findByStatus(any(), any());
    }

    @Test
    void findAllWithStatusFiltersByStatus() {
        Pageable pageable = Pageable.ofSize(20);
        when(courierRepository.findByStatus(CourierStatus.ACTIVO, pageable))
            .thenReturn(new PageImpl<>(List.of()));

        courierService.findAll(CourierStatus.ACTIVO, pageable);

        verify(courierRepository).findByStatus(CourierStatus.ACTIVO, pageable);
    }

    @Test
    void changeStatusUpdatesCourierToInactive() {
        UUID id = UUID.randomUUID();
        Courier courier = new Courier("Luis", "MOTO");
        when(courierRepository.findById(id)).thenReturn(Optional.of(courier));

        CourierResponse response = courierService.changeStatus(id, CourierStatus.INACTIVO);

        assertThat(response.status()).isEqualTo(CourierStatus.INACTIVO);
        assertThat(courier.isAvailable()).isFalse();
    }

    @Test
    void changeStatusThrowsWhenCourierDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(courierRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.changeStatus(id, CourierStatus.INACTIVO))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
