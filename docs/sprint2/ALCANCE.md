# Cobertura de la consigna del Sprint 2

La cantidad de issues es una decisión de organización del equipo, no una cantidad exigida por el PDF. Se reutiliza la implementación del Sprint 1; las nuevas issues aún están pendientes. Los códigos enlazan a las issues reales desde [BACKLOG.md](BACKLOG.md).

| Requisito de la consigna | Issues responsables |
|---|---|
| Conservar altas, consultas, tarifas, cancelación, estados e historial de envíos | S2-02, S2-03, S2-07 |
| Retirar administración local de repartidores y reemplazar relación JPA por identificador | S2-02, S2-21 |
| Consultar y validar repartidores mediante REST, incluyendo errores de comunicación | S2-01, S2-03, S2-23 |
| Servicio independiente courier con MySQL, JPA, repositorios, servicios, DTOs y errores globales | S2-21, S2-26 |
| Registrar, consultar, actualizar y gestionar estado/disponibilidad courier | S2-21, S2-22, S2-23 |
| Servicio security con usuarios y contraseñas almacenadas mediante hash seguro | S2-11, S2-12 |
| Registro, autenticación y consulta de usuario autenticado | S2-12, S2-13, S2-14 |
| JWT firmado, validación local de firma e integridad y expiración sin consulta HTTP por solicitud | S2-04, S2-13, S2-15, S2-18 |
| Roles ADMIN, CLIENTE, REPARTIDOR y pertenencia de recursos | S2-01, S2-04, S2-12, S2-14, S2-15, S2-18 |
| Config Server independiente con EnableConfigServer y backend Git | S2-24, S2-29 |
| Repositorio Git destinado a configuración dev/prod sin secretos | S2-25 |
| Tres clientes con spring.config.import y verificación al arrancar | S2-05, S2-16, S2-26 |
| Externalizar puertos, nombres, MySQL, URLs, logging y seguridad no sensible | S2-05, S2-16, S2-24, S2-25, S2-26 |
| Dockerfiles multietapa, Java 25, variables y ejecución independiente de cuatro imágenes | S2-06, S2-17, S2-24, S2-27 |
| Compose con cuatro aplicaciones, persistencias MySQL y DNS entre contenedores | S2-28 |
| Cuatro imágenes en Docker Hub con versión explícita y Compose sin build local | S2-09, S2-28, S2-30 |

## Criterios adicionales de calidad y colaboración

S2-08, S2-19, S2-20, S2-29 y S2-10 organizan CI, contrato OpenAPI, Postman, comprobaciones y documentación. Las suites S2-07, S2-18 y S2-09 aportan evidencia de conservación y funcionamiento; el PDF no exige una issue separada por suite. La revisión IA es una automatización solicitada por el equipo, no un requisito del PDF.

## Interpretaciones explícitas

- El texto marca la emisión y validación de JWT como «deseable» en una lista, pero luego exige que shipment y courier protejan endpoints y validen JWT localmente. Se incluye la solución completa para cubrir esa exigencia posterior.
- API Gateway no forma parte del sprint. No se agrega broker, frontend ni video como requisito de este PDF.
- Tres bases MySQL independientes corresponden a los tres servicios persistentes; Config Server usa Git, sin una cuarta base MySQL innecesaria.
- La integración final depende de imágenes y servicios reales. Cada integrante puede comenzar su carril con contratos y dobles; no se promete que todos los cierres de integración sean independientes.
