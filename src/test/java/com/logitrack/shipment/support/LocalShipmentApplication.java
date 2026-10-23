package com.logitrack.shipment.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.ShipmentServiceApplication;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.repository.CustomerRepository;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ejecutable de demostración: H2 volátil y dobles HTTP. Nunca se empaqueta en producción. */
public final class LocalShipmentApplication {
    private LocalShipmentApplication() { }

    static SpringApplicationBuilder application(LocalDependencies dependencies) {
        return new SpringApplicationBuilder(ShipmentServiceApplication.class).profiles("dev")
                .properties("CONFIG_SERVER_URL=" + dependencies.url())
                .initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("isolated-local-demo", dependencies.configuration())));
    }

    public static void main(String[] args) throws Exception {
        var dependencies = new LocalDependencies();
        Runtime.getRuntime().addShutdownHook(new Thread(dependencies::close));
        var context = application(dependencies).run("--spring.application.name=shipment-service", "--spring.profiles.active=dev",
                "--spring.config.import=configserver:" + dependencies.url());
        var customers = context.getBean(CustomerRepository.class);
        var a = customers.saveAndFlush(new Customer("Cliente demo A", "demo-a@example.test", "123"));
        var b = customers.saveAndFlush(new Customer("Cliente demo B", "demo-b@example.test", "456"));
        Map<String,Object> fixture = new LinkedHashMap<>();
        fixture.put("mode", "simulated-local");
        fixture.put("baseUrl", "http://localhost:8081");
        fixture.put("customerA", a.getId()); fixture.put("customerB", b.getId());
        fixture.put("courierA", JwtTestSupport.identityLink("repartidor-a", "courierId"));
        fixture.put("courierB", JwtTestSupport.identityLink("repartidor-b", "courierId"));
        fixture.put("admin", JwtTestSupport.token("admin"));
        fixture.put("clientA", JwtTestSupport.token("cliente-a", Map.of("customerId", a.getId().toString())));
        fixture.put("clientB", JwtTestSupport.token("cliente-b", Map.of("customerId", b.getId().toString())));
        fixture.put("courierTokenA", JwtTestSupport.token("repartidor-a"));
        fixture.put("courierTokenB", JwtTestSupport.token("repartidor-b"));
        Path output = Path.of("target/local-demo/session.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), fixture);
        System.out.println("DEMO SIMULADA lista: http://localhost:8081/docs/ | H2 volátil; no usa la base del Sprint 1.");
        System.out.println("JWT efímeros solo en target/local-demo/session.json (no se imprimen).");
    }
}
