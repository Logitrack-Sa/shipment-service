
package com.logitrack.testfixtures;

import com.logitrack.shipment.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_samples")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditSample extends BaseEntity {

    @Column(nullable = false)
    private String name;

    public AuditSample(String name) {
        this.name = name;
    }
    public void rename(String name) {
        this.name = name;
    }
}
