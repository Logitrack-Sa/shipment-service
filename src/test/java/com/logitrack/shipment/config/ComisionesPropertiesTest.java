package com.logitrack.shipment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static com.logitrack.shipment.config.BindFailureAssert.fallaArranquePorCampo;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ComisionesPropertiesTest {

    private final ApplicationContextRunner runnerConYaml =
            new ApplicationContextRunner()
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(Config.class);

    private final ApplicationContextRunner runnerSinYaml =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ComisionesProperties.class)
    static class Config {}

    @Test
    void vinculaValoresPorDefectoDesdeApplicationYaml() {
        runnerConYaml.run(
                context -> {
                    ComisionesProperties props = context.getBean(ComisionesProperties.class);
                    assertThat(props.cuentaCorriente())
                            .isEqualByComparingTo(new BigDecimal("5000.00"));
                    assertThat(props.cajaAhorro()).isEqualByComparingTo(new BigDecimal("2000.00"));
                    assertThat(props.cron()).isEqualTo("0 0 0 1 * *");
                });
    }

    @Test
    void vinculaValoresSobrescritos() {
        runnerConYaml
                .withPropertyValues(
                        "app.comisiones.cuenta-corriente=100.00",
                        "app.comisiones.caja-ahorro=50.00",
                        "app.comisiones.cron=0 * * * * *")
                .run(
                        context -> {
                            ComisionesProperties props = context.getBean(ComisionesProperties.class);
                            assertThat(props.cuentaCorriente())
                                    .isEqualByComparingTo(new BigDecimal("100.00"));
                            assertThat(props.cajaAhorro())
                                    .isEqualByComparingTo(new BigDecimal("50.00"));
                            assertThat(props.cron()).isEqualTo("0 * * * * *");
                        });
    }

    @Test
    void fallaAlArrancarSiFaltaUnaPropiedad() {
        runnerSinYaml
                .withPropertyValues("app.comisiones.caja-ahorro=2000.00")
                .run(context -> fallaArranquePorCampo(context, "cuentaCorriente"));
    }

    @Test
    void fallaAlArrancarSiElImporteEsNulo() {
        runnerConYaml
                .withPropertyValues("app.comisiones.cuenta-corriente=")
                .run(context -> fallaArranquePorCampo(context, "cuentaCorriente"));
    }

    @Test
    void fallaAlArrancarSiElImporteEsNegativo() {
        runnerSinYaml
                .withPropertyValues(
                        "app.comisiones.cuenta-corriente=1.00",
                        "app.comisiones.caja-ahorro=-1",
                        "app.comisiones.cron=0 0 0 1 * *")
                .run(context -> fallaArranquePorCampo(context, "cajaAhorro"));
    }

    @Test
    void fallaAlArrancarSiElCronEsInvalido() {
        runnerSinYaml
                .withPropertyValues(
                        "app.comisiones.cuenta-corriente=1.00",
                        "app.comisiones.caja-ahorro=1.00",
                        "app.comisiones.cron=no-es-un-cron")
                .run(context -> fallaArranquePorCampo(context, "cron"));
    }

    @Test
    void fallaAlArrancarSiElCronEsNulo() {
        runnerConYaml
                .withPropertyValues("app.comisiones.cron=")
                .run(context -> fallaArranquePorCampo(context, "cron"));
    }
}
