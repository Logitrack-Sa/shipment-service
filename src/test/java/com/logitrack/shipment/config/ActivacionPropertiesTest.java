package com.logitrack.shipment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static com.logitrack.shipment.config.BindFailureAssert.fallaArranquePorCampo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ActivacionPropertiesTest {

    private final ApplicationContextRunner runnerConYaml =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(Config.class);

    private final ApplicationContextRunner runnerSinYaml =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ActivacionProperties.class)
    static class Config {}

    @Test
    void vinculaValorPorDefectoDesdeApplicationYaml() {
        runnerConYaml.run(
                context -> {
                    ActivacionProperties props = context.getBean(ActivacionProperties.class);
                    assertThat(props.tokenValidezHoras()).isEqualTo(24L);
                });
    }

    @Test
    void vinculaValorSobrescrito() {
        runnerConYaml
                .withPropertyValues("app.activacion.token-validez-horas=48")
                .run(
                        context -> {
                            ActivacionProperties props = context.getBean(ActivacionProperties.class);
                            assertThat(props.tokenValidezHoras()).isEqualTo(48L);
                        });
    }

    @Test
    void fallaAlArrancarSiFaltaLaPropiedad() {
        runnerSinYaml.run(context -> fallaArranquePorCampo(context, "tokenValidezHoras"));
    }

    @Test
    void fallaAlArrancarSiElValorEsNulo() {
        runnerConYaml
                .withPropertyValues("app.activacion.token-validez-horas=")
                .run(context -> fallaArranquePorCampo(context, "tokenValidezHoras"));
    }

    @Test
    void fallaAlArrancarSiElValorEsCero() {
        runnerSinYaml
                .withPropertyValues("app.activacion.token-validez-horas=0")
                .run(context -> fallaArranquePorCampo(context, "tokenValidezHoras"));
    }

    @Test
    void fallaAlArrancarSiElValorEsNegativo() {
        runnerSinYaml
                .withPropertyValues("app.activacion.token-validez-horas=-1")
                .run(context -> fallaArranquePorCampo(context, "tokenValidezHoras"));
    }
}
