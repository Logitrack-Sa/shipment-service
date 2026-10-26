# Arquitectura prevista y estado comprobable del Sprint 2

```mermaid
flowchart LR
    Usuario[Cliente HTTP / Postman] -->|login| Security[security-service :8083]
    Usuario -->|Bearer JWT| Shipment[shipment-service :8081]
    Usuario -->|Bearer JWT ADMIN| Courier[courier-service :8082]
    Shipment -->|REST y Bearer validado| Courier
    Security --> SecurityDB[(MySQL security)]
    Shipment --> ShipmentDB[(MySQL shipment)]
    Courier --> CourierDB[(MySQL courier)]
    Shipment -. spring.config.import .-> Config[config-server :8888]
    Courier -. spring.config.import .-> Config
    Security -. spring.config.import .-> Config
    Config --> Git[config-repo Git dev/prod]
```

Cuatro aplicaciones, tres MySQL y un repositorio Git de propiedades. Sin API Gateway. Las flechas describen el contrato de la entrega; no demuestran que los cuatro servicios ya estén implementados.

- Security emite JWT RS256. Shipment y courier validan localmente con la clave pública; no consultan a security por cada petición.
- Shipment conserva clientes, envíos, tarifas, auditoría y courierId escalar. Courier es propietario de sus repartidores; no se comparten tablas ni entidades JPA.
- ADMIN administra. CLIENTE usa customerId confiable; REPARTIDOR usa courierId confiable. El UUID de usuario no reemplaza esos vínculos.
- Dentro de Compose se usan nombres DNS de servicios; localhost solo desde el host. Cada MySQL tiene usuario/volumen propio.
- Config entrega propiedades no sensibles; contraseñas y claves privadas se montan o inyectan en runtime. Config Server no necesita una cuarta base.

## Lo que se prueba hoy

Shipment tiene pruebas unitarias, MySQL, migración y arranque en contenedor con dobles HTTP. `Sprint2_Demo_H2` ejecuta la aplicación en IntelliJ con H2 volátil, un Config simulado y un Courier simulado; firma y autorización JWT son reales pero las claves son efímeras de test. No equivale al conjunto distribuido.

Hay implementaciones propuestas de courier y Config Server, una base de usuarios/roles y propiedades dev/prod. Sus PR y ramas todavía deben integrarse; no hay login/JWT de security ni cuatro imágenes publicadas verificadas al 26/10/2026. La validación real queda condicionada a esas entregas, cuentas vinculadas y Compose image-only (#61/#64/#65). [Estado por issue](ESTADO-ENTREGA.md) · [Requisitos y comando de integración](INTEGRACION.md).
