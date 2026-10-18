package com.logitrack.shipment.entity;

import com.logitrack.shipment.config.JpaAuditingConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
class BaseEntityTest {
    @Autowired
    private EntityManager entityManager;

    @Test
    void generatesUuidAndAuditOnInsertAndUpdate() {
        AuditSample sample = new AuditSample("original");
        entityManager.persist(sample);
        entityManager.flush();

        assertThat(sample.getId()).isNotNull();
        assertThat(sample.getCreatedAt()).isNotNull();
        assertThat(sample.getUpdatedAt()).isNotNull();
        assertThat(sample.getCreatedBy()).isEqualTo("system");
        assertThat(sample.getLastModifiedBy()).isEqualTo("system");
        assertThat(sample.getVersion()).isZero();

        var createdAt = sample.getCreatedAt();
        var updatedAt = sample.getUpdatedAt();
        sample.name = "modified";
        entityManager.flush();

        assertThat(sample.getCreatedAt()).isEqualTo(createdAt);
        assertThat(sample.getUpdatedAt()).isAfter(updatedAt);
        assertThat(sample.getVersion()).isEqualTo(1L);
    }

    @Test
    void keepsHashStableAndComparesHibernateProxiesById() {
        AuditSample sample = new AuditSample("sample");
        var set = new HashSet<BaseEntity>();
        set.add(sample);
        assertThat(sample).isNotEqualTo(new AuditSample("sample"));

        entityManager.persist(sample);
        entityManager.flush();
        entityManager.clear();
        AuditSample proxy = entityManager.getReference(AuditSample.class, sample.getId());

        assertThat(sample).isEqualTo(proxy);
        assertThat(proxy).isEqualTo(sample);
        assertThat(set).contains(proxy);
        assertThat(sample).isNotEqualTo(null);
    }
}

@Entity
@Table(name = "audit_samples")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuditSample extends BaseEntity {
    @Column(nullable = false)
    String name;

    AuditSample(String name) {
        this.name = name;
    }
}
