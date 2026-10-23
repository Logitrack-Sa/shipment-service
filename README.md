# LogiTrack · shipment-service

[![CI](https://github.com/Logitrack-Sa/shipment-service/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Logitrack-Sa/shipment-service/actions/workflows/ci.yml)

API de envíos con Java 25, Spring Boot 3.5.16, Spring Cloud 2025.0.3, JPA, MySQL y Flyway. Conserva tarifa, tracking, estados, cancelación y auditoría del Sprint 1. Courier es un UUID externo, consultado por REST; sus datos se administran en courier-service.

## Probar en IntelliJ

[Configuraciones listas: Sprint2_Pruebas_Local y Sprint2_Demo_H2](docs/sprint2/INTELLIJ.md). La demo usa H2 volátil, claves efímeras y dobles HTTP; no toca datos existentes ni representa cuatro servicios reales.

## Ejecutar

Abrir pom.xml en IntelliJ con JDK25. Los perfiles dev/prod requieren Config Server, MySQL propio y la clave pública RSA del emisor JWT. Configurar las variables indicadas en [arranque y configuración](docs/sprint2/CONFIGURACION-SHIPMENT.md); ejecutar ShipmentServiceApplication o `./mvnw spring-boot:run` (`.\mvnw.cmd` en Windows). El puerto llega del servidor de configuración; la plantilla propone 8081.

El Dockerfile es multietapa Java25, con bases fijadas por digest, contexto limitado a pom/src-main y usuario10001. Puerto, perfil, Config Server, MySQL y clave pública llegan por entorno/montajes. El Compose distribuido de #64/#65 sigue pendiente; `.github/compose.smoke.yaml` solo se usa en CI con dobles HTTP.

## Seguridad y API

JWT RS256 validado localmente: firma, issuer, audience, expiración, iat, kid y roles. Sin llamadas al emisor por solicitud. ADMIN administra; CLIENTE crea/consulta sus envíos; REPARTIDOR consulta y cambia estados de los asignados. Cancelación y asignación son administrativas. Listados filtran por pertenencia antes de paginar. [Matriz de permisos y contratos](docs/sprint2/CONTRATOS-V1.md).

Documentación pública, sin datos de negocio:
- `/docs/`: portal con campo JWT por solicitud, sin persistencia del token.
- `/docs/explorer.html`: Swagger con Authorize → Bearer.
- `/v3/api-docs`: OpenAPI; `/actuator/health`: estado mínimo.

| Método | Ruta | Operación |
|---|---|---|
| POST | /api/v1/customers | Registrar cliente (ADMIN) |
| GET | /api/v1/customers y /{id} | Consultar clientes (ADMIN) |
| POST | /api/v1/shipments | Registrar envío |
| GET | /api/v1/shipments | Página propia; filtro status opcional |
| GET | /api/v1/shipments/{id} | Detalle autorizado |
| GET | /api/v1/shipments/tracking/{trackingCode} | Seguimiento autorizado |
| GET | /api/v1/shipments/{id}/history | Historial autorizado |
| PUT | /api/v1/shipments/{id}/courier | Asignar UUID de courier disponible |
| PATCH | /api/v1/shipments/{id}/status | Transición válida |
| POST | /api/v1/shipments/{id}/cancellation | Cancelar PENDIENTE |

Tarifa = base + max(pesoKg, ancho×alto×largo/divisor) × precioPorKg. BigDecimal y redondeo final HALF_UP a dos decimales. Parámetros externos; ejemplo base1000, precio250, divisor5000: 2,5kg y 30×20×40cm →2200. Tracking `LT-` +10 caracteres seguros; restricción única y reintentos únicamente por colisión. Envío e historial se confirman juntos.

## Pruebas

`./mvnw verify` ejecuta tests unitarios, integración, JWT real, HTTP simulado y migraciones contra MySQL/Testcontainers; requiere Docker. JaCoCo: target/site/jacoco/index.html. [Comandos y límites sin Docker](docs/sprint2/CONFIGURACION-SHIPMENT.md).

CI prueba H2, repite con MySQL, verifica V1→V2 con datos anteriores y arranca shipment/MySQL en contenedores. El smoke usa dobles HTTP de Config/Courier y claves RSA efímeras; la integración con los cuatro servicios reales queda para #61.

## Arquitectura y entrega

[Diagrama de cuatro aplicaciones, tres MySQL y Git config](docs/sprint2/ARQUITECTURA.md). [Integración image-only y evidencia](docs/sprint2/INTEGRACION.md): `docker compose -f compose.release.yaml up -d --no-build` cuando #64/#65 entreguen el archivo y las imágenes. No hay tags/Docker Hub verificados todavía; sus enlaces se completan con evidencia de publicación. Los PR de componentes no esperan a otros servicios.

[Histórico del Sprint 1](docs/sprint1/README.md) preservado. [Migración de UUID y auditoría](docs/sprint2/MIGRACION-COURIER.md) sin borrar couriers hasta verificar traslado. Cuentas de prueba A/B vinculadas por ADMIN; ninguna contraseña se incluye en Git.

## Trabajo del Sprint 2

[Tablero](https://github.com/orgs/Logitrack-Sa/projects/2), [reparto](docs/sprint2/BACKLOG.md), [alcance PDF](docs/sprint2/ALCANCE.md), [workflow](docs/sprint2/WORKFLOW.md), [migración courier sin pérdida](docs/sprint2/MIGRACION-COURIER.md). No modificar migraciones aplicadas ni compartir tablas/entidades con otros servicios.

Inyección por constructor, DTOs inmutables, validación, transacciones en servicios y errores sin detalles internos. Ramas por tarea, commits breves en español, PR vinculado y revisión de otro integrante antes de main. La IA comenta hallazgos; no reemplaza CI ni aprobación humana.
