package com.logitrack.shipmentservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static com.logitrack.shipmentservice.config.BindFailureAssert.fallaArranquePorCampo;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LimitesPropertiesTest {

    private final ApplicationContextRunner runnerConYaml =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(Config.class);

    private final ApplicationContextRunner runnerSinYaml =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LimitesProperties.class)
    static class Config {}

    @Test
    void vinculaValoresPorDefectoDesdeApplicationYaml() {
        runnerConYaml.run(
                context -> {
                    LimitesProperties props = context.getBean(LimitesProperties.class);
                    assertThat(props.extraccionDiaria().titular())
                            .isEqualByComparingTo(new BigDecimal("100000.00"));
                    assertThat(props.extraccionDiaria().adherente())
                            .isEqualByComparingTo(new BigDecimal("70000.00"));
                });
    }

    @Test
    void vinculaValoresSobrescritos() {
        runnerConYaml
                .withPropertyValues(
                        "app.limites.extraccion-diaria.titular=150000.00",
                        "app.limites.extraccion-diaria.adherente=500.50")
                .run(
                        context -> {
                            LimitesProperties props = context.getBean(LimitesProperties.class);
                            assertThat(props.extraccionDiaria().titular())
                                    .isEqualByComparingTo(new BigDecimal("150000.00"));
                            assertThat(props.extraccionDiaria().adherente())
                                    .isEqualByComparingTo(new BigDecimal("500.50"));
                        });
    }

    @Test
    void fallaAlArrancarSiFaltaLaPropiedad() {
        runnerSinYaml.run(context -> fallaArranquePorCampo(context, "extraccionDiaria"));
    }

    @Test
    void fallaAlArrancarSiElImporteEsNulo() {
        runnerConYaml
                .withPropertyValues("app.limites.extraccion-diaria.titular=")
                .run(context -> fallaArranquePorCampo(context, "titular"));
    }

    @Test
    void fallaAlArrancarSiElImporteDelTitularEsNegativo() {
        runnerSinYaml
                .withPropertyValues(
                        "app.limites.extraccion-diaria.titular=-0.01",
                        "app.limites.extraccion-diaria.adherente=1.00")
                .run(context -> fallaArranquePorCampo(context, "titular"));
    }

    @Test
    void fallaAlArrancarSiElImporteDelAdherenteEsNegativo() {
        runnerSinYaml
                .withPropertyValues(
                        "app.limites.extraccion-diaria.titular=1.00",
                        "app.limites.extraccion-diaria.adherente=-1")
                .run(context -> fallaArranquePorCampo(context, "adherente"));
    }

    @Test
    void aceptaCeroComoImporteValido() {
        runnerSinYaml
                .withPropertyValues(
                        "app.limites.extraccion-diaria.titular=0",
                        "app.limites.extraccion-diaria.adherente=0")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
