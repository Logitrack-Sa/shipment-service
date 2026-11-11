package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.support.ShipmentEntities;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.logitrack.shipment.support.ShipmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {
    @Mock
    private ShipmentRepository shipments;
    @Mock
    private CustomerRepository customers;
    @Mock
    private ShipmentStatusRecorder recorder;
    @Mock
    private TrackingCodeGenerator trackingCodes;

    private ShipmentService service;

    @BeforeEach
    void createService() {
        var mapper = new ShipmentMapper();
        var fees = new FeeCalculator(new BigDecimal("1000"), new BigDecimal("250"), new BigDecimal("5000"));
        var creator = new ShipmentCreator(customers, shipments, fees, mapper, recorder);
        service = new ShipmentService(shipments, creator, trackingCodes, mapper);
    }

    @Nested
    class CreateShipment {
        @Test
        void createsPendingShipmentWithFeeAndInitialHistory() {
            givenCustomerAndPersistence();
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE);

            var response = service.createShipment(request());

            assertThat(response.id()).isEqualTo(SHIPMENT_ID);
            assertThat(response.status()).isEqualTo(ShipmentStatus.PENDIENTE);
            assertThat(response.fee()).isEqualTo(new BigDecimal("2200.00"));
            assertThat(response.trackingCode()).matches("LT-[A-Z0-9]{10}");
            assertThat(response.courierId()).isNull();
            verify(recorder).record(any(Shipment.class), isNull(), eq(ShipmentStatus.PENDIENTE), eq("Alta del envío"));
        }

        @ParameterizedTest
        @CsvSource({
                "10, 10, 10, 10, 3500.00",
                "2.5, 30, 20, 40, 2200.00",
                "0.1, 10.01, 10.01, 10.01, 1050.15"
        })
        void calculatesExactFeeForRealVolumetricAndRoundingCases(
                BigDecimal weight, BigDecimal width, BigDecimal height, BigDecimal length, BigDecimal fee) {
            givenCustomerAndPersistence();
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE);
            var fixture = request();
            var request = new CreateShipmentRequest(CUSTOMER_ID, fixture.origin(), fixture.destination(),
                    weight, width, height, length);

            assertThat(service.createShipment(request).fee()).isEqualTo(fee);
        }

        @Test
        void rejectsMissingCustomerWithoutSavingAnything() {
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE);
            when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createShipment(request())).isInstanceOf(ResourceNotFoundException.class);
            verify(shipments, never()).saveAndFlush(any());
            verifyNoInteractions(recorder);
        }

        @Test
        void generatesAnotherCodeWhenItAlreadyExists() {
            givenCustomerAndPersistence();
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE, "LT-0123456789");
            when(shipments.existsByTrackingCode(TRACKING_CODE)).thenReturn(true);

            assertThat(service.createShipment(request()).trackingCode()).isEqualTo("LT-0123456789");
            verify(shipments).saveAndFlush(any());
        }

        @Test
        void retriesACodeCollisionDetectedDuringInsert() {
            givenCustomer();
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE, "LT-0123456789");
            when(shipments.existsByTrackingCode(TRACKING_CODE)).thenReturn(false, true);
            doThrow(new DataIntegrityViolationException("Tracking duplicado"))
                    .doAnswer(invocation -> identified(invocation.getArgument(0)))
                    .when(shipments).saveAndFlush(any());

            assertThat(service.createShipment(request()).trackingCode()).isEqualTo("LT-0123456789");
            verify(shipments, times(2)).saveAndFlush(any());
            verify(recorder).record(any(), isNull(), eq(ShipmentStatus.PENDIENTE), anyString());
        }

        @Test
        void propagatesAnIntegrityFailureUnrelatedToTracking() {
            givenCustomer();
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE);
            var failure = new DataIntegrityViolationException("Otro conflicto");
            doThrow(failure).when(shipments).saveAndFlush(any());

            assertThatThrownBy(() -> service.createShipment(request())).isSameAs(failure);
            verify(trackingCodes).generate();
            verifyNoInteractions(recorder);
        }

        @Test
        void stopsAfterFiveCollisions() {
            when(trackingCodes.generate()).thenReturn(TRACKING_CODE);
            when(shipments.existsByTrackingCode(TRACKING_CODE)).thenReturn(true);

            assertThatThrownBy(() -> service.createShipment(request())).isInstanceOf(DuplicateResourceException.class);
            verify(trackingCodes, times(5)).generate();
            verify(shipments, never()).saveAndFlush(any());
            verifyNoInteractions(customers, recorder);
        }

        private void givenCustomerAndPersistence() {
            givenCustomer();
            when(shipments.saveAndFlush(any())).thenAnswer(invocation -> identified(invocation.getArgument(0)));
        }

        private void givenCustomer() {
            when(customers.findById(CUSTOMER_ID)).thenReturn(Optional.of(ShipmentEntities.customer()));
        }

        private Shipment identified(Shipment shipment) {
            ReflectionTestUtils.setField(shipment, "id", SHIPMENT_ID);
            return shipment;
        }
    }

    @Nested
    class QueryShipments {
        @Test
        void findsExistingShipmentById() {
            when(shipments.findById(SHIPMENT_ID)).thenReturn(Optional.of(ShipmentEntities.shipment()));

            assertThat(service.findById(SHIPMENT_ID).trackingCode()).isEqualTo(TRACKING_CODE);
        }

        @Test
        void rejectsUnknownId() {
            when(shipments.findById(SHIPMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(SHIPMENT_ID)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void findsExistingTrackingCode() {
            when(shipments.findByTrackingCode(TRACKING_CODE)).thenReturn(Optional.of(ShipmentEntities.shipment()));

            assertThat(service.findByTrackingCode(TRACKING_CODE).id()).isEqualTo(SHIPMENT_ID);
        }

        @Test
        void rejectsUnknownTrackingCode() {
            when(shipments.findByTrackingCode(TRACKING_CODE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findByTrackingCode(TRACKING_CODE))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void filtersByStatusWithPagination() {
            var pageable = PageRequest.of(1, 1);
            when(shipments.findByStatus(ShipmentStatus.PENDIENTE, pageable))
                    .thenReturn(new PageImpl<>(List.of(ShipmentEntities.shipment()), pageable, 3));

            var response = service.findAll(ShipmentStatus.PENDIENTE, pageable);

            assertThat(response.content()).singleElement().satisfies(shipment -> assertThat(shipment.id()).isEqualTo(SHIPMENT_ID));
            assertThat(response.page()).isEqualTo(1);
            assertThat(response.size()).isEqualTo(1);
            assertThat(response.totalElements()).isEqualTo(3);
            assertThat(response.totalPages()).isEqualTo(3);
            verify(shipments, never()).findAll(any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        void listsAllWhenStatusIsAbsent() {
            var pageable = PageRequest.of(0, 20);
            when(shipments.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

            assertThat(service.findAll(null, pageable).content()).isEmpty();
            verify(shipments, never()).findByStatus(any(), any());
        }
    }
}
