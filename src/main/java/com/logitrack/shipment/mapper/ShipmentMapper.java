package com.logitrack.shipment.mapper;

import com.logitrack.shipment.dto.AddressDto;
import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Address;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.Shipment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ShipmentMapper {
    public Shipment toEntity(CreateShipmentRequest request, Customer customer, String code, BigDecimal fee) {
        return Shipment.builder().customer(customer).trackingCode(code).fee(fee)
                .origin(toAddress(request.origin())).destination(toAddress(request.destination()))
                .weightKg(request.weightKg()).widthCm(request.widthCm())
                .heightCm(request.heightCm()).lengthCm(request.lengthCm()).build();
    }

    public ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(), shipment.getTrackingCode(), shipment.getCustomer().getId(),
                toDto(shipment.getOrigin()), toDto(shipment.getDestination()),
                shipment.getWeightKg(), shipment.getWidthCm(), shipment.getHeightCm(),
                shipment.getLengthCm(), shipment.getFee(), shipment.getStatus(),
                shipment.getCourier() == null ? null : shipment.getCourier().getId(),
                shipment.getCreatedAt(), shipment.getUpdatedAt(),
                shipment.getCreatedBy(), shipment.getLastModifiedBy(), shipment.getVersion());
    }

    private Address toAddress(AddressDto address) {
        return new Address(address.street(), address.number(), address.city(), address.postalCode());
    }

    private AddressDto toDto(Address address) {
        return new AddressDto(address.getStreet(), address.getNumber(), address.getCity(), address.getPostalCode());
    }
}
