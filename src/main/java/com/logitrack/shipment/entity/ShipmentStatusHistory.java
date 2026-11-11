package com.logitrack.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "shipment_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentStatusHistory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false, updatable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private ShipmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ShipmentStatus newStatus;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    @Column(updatable = false, length = 255)
    private String observation;

    public ShipmentStatusHistory(Shipment shipment, ShipmentStatus previousStatus,
                                 ShipmentStatus newStatus, Instant changedAt, String observation) {
        this.shipment = shipment;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedAt = changedAt;
        this.observation = observation;
    }
}
