package com.logitrack.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "customers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "owner_user_id", unique = true, updatable = false)
    private UUID ownerUserId;

    public Customer(UUID ownerUserId, String name, String email) {
        this(name, email, null);
        this.ownerUserId = ownerUserId;
    }

    public Customer(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }
}
