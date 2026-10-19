package com.logitrack.shipment.entity;

import com.logitrack.shipment.exception.InvalidStateTransitionException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
@Entity
@Table(name = "shipments",
        uniqueConstraints = @UniqueConstraint(name = "uk_shipments_tracking_code", columnNames = "tracking_code"),
        indexes = @Index(name = "idx_shipments_status", columnList = "status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment extends BaseEntity {
    @Column(nullable = false, updatable = false, length = 13)
    private String trackingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "origin_street", nullable = false, length = 150)),
            @AttributeOverride(name = "number", column = @Column(name = "origin_number", nullable = false, length = 20)),
            @AttributeOverride(name = "city", column = @Column(name = "origin_city", nullable = false, length = 100)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "origin_postal_code", nullable = false, length = 20))
    })
    private Address origin;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "destination_street", nullable = false, length = 150)),
            @AttributeOverride(name = "number", column = @Column(name = "destination_number", nullable = false, length = 20)),
            @AttributeOverride(name = "city", column = @Column(name = "destination_city", nullable = false, length = 100)),
            @AttributeOverride(name = "postalCode", column = @Column(name = "destination_postal_code", nullable = false, length = 20))
    })
    private Address destination;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthCm;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private Courier courier;

    @Builder
    private Shipment(String trackingCode, Customer customer, Address origin, Address destination,
                     BigDecimal weightKg, BigDecimal widthCm, BigDecimal heightCm,
                     BigDecimal lengthCm, BigDecimal fee) {
        this.trackingCode = trackingCode;
        this.customer = customer;
        this.origin = origin;
        this.destination = destination;
        this.weightKg = weightKg;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.lengthCm = lengthCm;
        this.fee = fee;
    }

    public void assignCourier(Courier courier) {
        this.courier = Objects.requireNonNull(
            courier,
            "El repartidor no puede ser nulo"
        );
    }

    /**
     * Cambia el estado del envío validando antes la transición contra la máquina de estados
     * definida en {@link ShipmentStatus}.
     *
     * @param target estado destino del envío
     * @throws NullPointerException            si {@code target} es {@code null}
     * @throws InvalidStateTransitionException si la transición desde el estado actual no está permitida
     */
    public void changeStatusTo(ShipmentStatus target) {
        Objects.requireNonNull(target, "El estado destino no puede ser nulo");
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Envío", status.name(), target.name());
        }
        this.status = target;
    }
}
