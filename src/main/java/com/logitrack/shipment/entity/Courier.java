package com.logitrack.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "couriers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Courier extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourierStatus status = CourierStatus.ACTIVO;

    public Courier(String name, String vehicleType) {
        this.name = name;
        this.vehicleType = vehicleType;
    }
}
