package com.logitrack.shipment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingCodeGeneratorTest {
    @Test
    void generatesTenUppercaseAlphanumericCharactersAfterThePrefix() {
        var generator = new TrackingCodeGenerator();
        for (int index = 0; index < 20; index++) {
            assertThat(generator.generate()).matches("LT-[A-Z0-9]{10}");
        }
    }
}
