package com.logitrack.shipment.support;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class LocalDemoIsolationTest {
    @Test void demoNeverUsesAnExternalDatasourceEvenWhenConfiguredByCaller() throws Exception {
        try (var dependencies = new LocalDependencies();
             var context = LocalShipmentApplication.application(dependencies)
                 .initializers(app -> app.getEnvironment().getPropertySources().addFirst(
                         new MapPropertySource("test-port", Map.of("server.port", 0))))
                 .run("--spring.application.name=shipment-service", "--spring.profiles.active=dev", "--spring.config.import=configserver:" + dependencies.url(),
                         "--spring.datasource.url=jdbc:must-not-use:external")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:shipment_demo");
            assertThat(context.getEnvironment().getProperty("spring.flyway.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
        }
    }
}
