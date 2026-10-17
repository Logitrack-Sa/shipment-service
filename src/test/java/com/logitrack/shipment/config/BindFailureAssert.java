package com.logitrack.shipment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.boot.test.context.assertj.AssertableApplicationContext;

final class BindFailureAssert {

    private BindFailureAssert() {}

    static void fallaArranquePorCampo(AssertableApplicationContext context, String campo) {
        assertThat(context).hasFailed();
        StringBuilder mensajes = new StringBuilder();
        for (Throwable causa = context.getStartupFailure();
                causa != null;
                causa = causa.getCause()) {
            mensajes.append(causa.getMessage()).append('\n');
        }
        assertThat(mensajes.toString()).contains(campo);
    }
}
