package com.logitrack.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "couriers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Courier extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "vehicle_type", nullable = false, length = 50)
    private String vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierStatus status = CourierStatus.ACTIVO;

    public Courier(String name, String vehicleType) {
        this.name = name;
        this.vehicleType = vehicleType;
    }

    public void changeStatus(CourierStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus, "El estado no puede ser nulo");
    }

    public boolean isAvailable() {
        return status == CourierStatus.ACTIVO;
    }
}
