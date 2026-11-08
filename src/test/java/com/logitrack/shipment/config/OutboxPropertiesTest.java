package com.logitrack.shipment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxPropertiesTest {

    @Test
    void acceptsDefaultConfiguration() {
        new OutboxProperties().validate();
    }

    @Test
    void rejectsNonPositiveMaxConcurrentSends() {
        OutboxProperties properties = new OutboxProperties();
        properties.setMaxConcurrentSends(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-concurrent-sends");
    }

    @Test
    void rejectsNaNRetryMultiplier() {
        OutboxProperties properties = new OutboxProperties();
        properties.setRetryMultiplier(Double.NaN);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-multiplier");
    }

    @Test
    void rejectsPositiveInfinityRetryMultiplier() {
        OutboxProperties properties = new OutboxProperties();
        properties.setRetryMultiplier(Double.POSITIVE_INFINITY);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-multiplier");
    }

    @Test
    void rejectsNegativeInfinityRetryMultiplier() {
        OutboxProperties properties = new OutboxProperties();
        properties.setRetryMultiplier(Double.NEGATIVE_INFINITY);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry-multiplier");
    }

    @Test
    void rejectsOverflowAddingSendAndConfirmTimeouts() {
        OutboxProperties properties = new OutboxProperties();
        properties.setSendTimeoutMs(Long.MAX_VALUE);
        properties.setConfirmTimeoutMs(1);
        properties.setLockLeaseMs(Long.MAX_VALUE);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("excede el rango");
    }
}
