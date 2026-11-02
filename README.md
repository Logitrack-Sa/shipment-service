# LogiTrack · shipment-service

[![CI](https://github.com/Logitrack-Sa/shipment-service/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Logitrack-Sa/shipment-service/actions/workflows/ci.yml)

API de gestión de envíos. Java 25, Spring Boot 3.5, Spring Cloud 2025.0 (Config, Eureka Client, OpenFeign, Resilience4j) y Maven Wrapper.

## Desarrollo

1. Instalar JDK 25 y configurar `JAVA_HOME`.
2. Abrir `pom.xml` en IntelliJ IDEA como proyecto Maven; seleccionar JDK 25.
3. Configurar `DB_URL` (`DB_HOST`, `DB_PORT`, `DB_NAME`), `DB_USERNAME`, `DB_PASSWORD`, `CONFIG_SERVER_URL` (`http://localhost:8888`) y `EUREKA_SERVER_URL` (`http://localhost:8065/eureka/`) según `.env.example`. Spring no carga `.env` automáticamente: definir las variables en la configuración de ejecución del IDE o en el entorno.
4. Con MySQL (`:3306`), Config Server (`config-server:8888`), Eureka Server (`eureka-server:8065`) y `courier-service` (`courier-service:8082`) disponibles: `./mvnw spring-boot:run` (`.\mvnw.cmd` en Windows). Para ejecución local aislada con H2 y dobles HTTP sin servicios externos, usar `LocalShipmentApplication` (`Sprint2_Demo_H2`).

La aplicación expone `/actuator/health`, `/swagger-ui.html` y `/v3/api-docs`.
Las solicitudes externas ingresan a través del **API Gateway (`api-gateway:8080`)**, mientras que el descubrimiento dinámico de instancias se realiza mediante **Eureka Server (`eureka-server:8065`)**.

El archivo `compose.yaml` local de este repositorio levanta `mysql` (MySQL 8.4) y `shipment-service` (`:8081`); para el entorno distribuido completo con `config-server`, `eureka-server`, `security-service`, `courier-service` y `api-gateway`, utilizar el Compose de despliegue integrado o `compose.release.yaml`.

## Pruebas

```shell
./mvnw verify
```

Las pruebas unitarias no requieren red ni base de datos. El perfil `test` usa H2
solo para contexto y persistencia; no reemplaza MySQL/Testcontainers de #30.
JaCoCo genera `target/site/jacoco/index.html`.

La CI ejecuta la suite con Temurin 25 y vuelve a probar alta, historial,
colisiones y rollback contra un MySQL 8.4 efímero. También verifica health,
Swagger y OpenAPI por HTTP. Ese smoke test crea el esquema únicamente en su
base aislada; la validación de migraciones productivas sigue en #27/#30.

La suite de comunicación Feign, descubrimiento dinámico y resiliencia (ver detalle en [docs/sprint3/TESTING-FEIGN.md](docs/sprint3/TESTING-FEIGN.md)) puede ejecutarse sin BD externa ni contenedores adicionales:
```shell
./mvnw test -Dtest=FeignCourierCommunicationIntegrationTest,HttpCourierLookupTest,AssignCourierTest,ShipmentServiceTest,FeeCalculatorTest,GlobalExceptionHandlerTest
```

## Convenciones

- Paquete raíz: `com.logitrack.shipment`. Capas: `controller`, `service`,
  `repository`, `entity`, `dto`, `mapper`, `exception`, `client` y `config`.
- Clases y paquetes en inglés; estados del negocio en español.
- Endpoints bajo `/api/v1`, con sustantivos plurales.
- DTOs inmutables, validación de entrada y mappers manuales breves. La API no
  expone entidades JPA ni `Page` de Spring.
- Inyección por constructor; transacciones en servicios; lecturas con
  `readOnly = true`; dinero con `BigDecimal`.
- Ramas `feat/s3-<issue>-descripcion`, `feature/<issue>-descripcion`, `chore/...`, `test/...` o `docs/...`.
- Commits Conventional Commits breves, con descripción en español.
- Cambios mediante PR con `Closes #N` y aprobación de otro integrante.
  Nadie publica directamente en `main`.

## Modelo y decisiones

Las entidades usan UUID y auditoría compartida. `courierId` es opcional hasta la
asignación; los cambios de estado requieren historial persistido. Peso y medidas
usan `BigDecimal` para evitar errores binarios en el cálculo de tarifa.

## Tarifa propuesta

`pesoVolumetricoKg = largoCm * anchoCm * altoCm / divisor`

`tarifa = base + max(pesoRealKg, pesoVolumetricoKg) * precioPorKg`

La fórmula es una propuesta del equipo pendiente de confirmación del docente.
Importes en pesos; peso en kg; medidas en cm. Se usa BigDecimal y se redondea
solo el resultado final a dos decimales con HALF_UP.

| Propiedad | Default | Variable de entorno |
| --- | --- | --- |
| `logitrack.fee.base` | `1000.00` | `FEE_BASE` |
| `logitrack.fee.price-per-kg` | `250.00` | `FEE_PRICE_PER_KG` |
| `logitrack.fee.volumetric-divisor` | `5000` | `FEE_VOLUMETRIC_DIVISOR` |

Ejemplo: 2,5 kg y 30 × 20 × 40 cm tienen peso facturable de 4,8 kg y tarifa
de 2200,00. Base y precio no negativos; divisor, peso y medidas positivos.
Cambiar la configuración requiere reiniciar la aplicación.

## Comunicación con `courier-service` y Resiliencia (OpenFeign + Eureka · Issues #84, #85 y #86)

`shipment-service` valida la disponibilidad y el estado de los repartidores comunicándose con `courier-service` mediante un cliente declarativo de **Spring Cloud OpenFeign** (`CourierFeignClient`), resolución dinámica de instancias a través de **Eureka Server (`:8065`)** y manejo resiliente de errores (`CourierErrorDecoder` + `CourierFeignFallbackFactory`):

- **Descubrimiento dinámico**: `@FeignClient(name = "${logitrack.courier.service-id:courier-service}", url = "${logitrack.courier.base-url:}", fallbackFactory = CourierFeignFallbackFactory.class)` localiza las instancias de `courier-service` registradas en Eureka sin requerir direcciones IP ni puertos fijos.
- **Endpoints consumidos**:
  - `GET /api/v1/couriers/{id}/availability` — valida disponibilidad al asignar un repartidor a un envío pendiente.
  - `GET /api/v1/internal/couriers/{id}/status` — valida que el repartidor asignado siga `ACTIVO` al pasar un envío a `EN_RUTA`.
- **Seguridad y propagación de JWT**: Las peticiones externas ingresan por el **API Gateway (`:8080`)** y `FeignCourierLookup` junto con `CourierClientConfig` propagan el encabezado `Authorization: Bearer <token>` validado en el `SecurityContextHolder` hacia `courier-service`.
- **Resiliencia y comportamiento cuando `courier-service` falla o no está disponible (Issue #85)**:
  - **`CourierErrorDecoder`**: Traduce respuestas HTTP no exitosas de `courier-service`:
    - `HTTP 404` $\rightarrow$ `ResourceNotFoundException` (`404 RESOURCE_NOT_FOUND`).
    - `HTTP 422` $\rightarrow$ `CourierNotActiveException` (`422 COURIER_NOT_ACTIVE`).
    - `HTTP 401` / `403` $\rightarrow$ `RemoteCourierException` (`401 COURIER_UNAUTHORIZED` / `403 COURIER_FORBIDDEN`).
    - `HTTP 500` / `503` / otros $\rightarrow$ `RemoteCourierException` (`503 COURIER_UNAVAILABLE`).
  - **`CourierFeignFallbackFactory` + Circuit Breaker**: Ante errores de transporte (`ConnectException`, timeouts, caída de `courier-service` o falta de instancias en Eureka), activa el fallback registrando el incidente con nivel `WARN` (sin exponer tokens ni datos sensibles) y lanza `RemoteCourierException` (`503 COURIER_UNAVAILABLE`), abortando cualquier asignación o transición de estado que no haya podido validarse.

| Propiedad | Default | Variable de entorno |
| --- | --- | --- |
| `eureka.client.service-url.defaultZone` | `http://localhost:8065/eureka/` | `EUREKA_SERVER_URL` |
| `spring.cloud.openfeign.circuitbreaker.enabled` | `true` | — |
| `spring.cloud.circuitbreaker.resilience4j.disable-time-limiter` | `true` | — |
| `logitrack.courier.service-id` | `courier-service` | — |
| `logitrack.courier.connect-timeout` | `500ms` | — |
| `logitrack.courier.read-timeout` | `1s` | — |
| `logitrack.courier.base-url` | *(opcional, vacío con Eureka)* | `COURIER_SERVICE_URL` |

## Manejo global de excepciones (Issue #17 y #85)

`GlobalExceptionHandler` (`@RestControllerAdvice`,
`com.logitrack.shipment.controller`) traduce las excepciones de dominio y de
framework a respuestas HTTP con un único formato `ErrorResponse`
(`com.logitrack.shipment.dto.ErrorResponse`):

```json
{
  "timestamp": "2026-10-17T20:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "INVALID_STATE_TRANSITION",
  "message": "Transición de estado inválida para Shipment: PENDIENTE -> ENTREGADO",
  "path": "/api/v1/shipments/11111111-2222-3333-4444-555555555555/estado",
  "details": [
    { "field": "estado", "message": "Valor inválido: ENTREGADO" }
  ]
}
```

`details` es **opcional**: solo aparece cuando hay violaciones de campos o parámetros
(validación, tipo inválido, etc.). `code` es un identificador estable pensado para que
los clientes reaccionen sin parsear el mensaje.

### Tabla excepción → HTTP

| Excepción | HTTP | `code` | `details` |
| --- | --- | --- | --- |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` | no |
| `DuplicateResourceException` | 409 | `DUPLICATE_RESOURCE` | no |
| `BusinessRuleException` (base) | 422 | `BUSINESS_RULE_VIOLATION` | no |
| `InvalidStateTransitionException` | 422 | `INVALID_STATE_TRANSITION` | no |
| `ShipmentCannotBeCancelledException` | 422 | `SHIPMENT_CANNOT_BE_CANCELLED` | no |
| `CourierNotActiveException` | 422 | `COURIER_NOT_ACTIVE` | no |
| `CourierNotAssignedException` | 422 | `COURIER_NOT_ASSIGNED` | no |
| `RemoteCourierException` | 401 / 403 / 503 | `COURIER_UNAUTHORIZED` / `COURIER_FORBIDDEN` / `COURIER_UNAVAILABLE` | no |
| `MethodArgumentNotValidException` (`@Valid` en el body) | 400 | `VALIDATION_ERROR` | sí, por campo |
| `ConstraintViolationException` (parámetros `@Validated`) | 400 | `CONSTRAINT_VIOLATION` | sí, por parámetro |
| `HttpMessageNotReadableException` (JSON malformado / enum inválido) | 400 | `MALFORMED_REQUEST` | no |
| `MethodArgumentTypeMismatchException` (UUID u otro tipo inválido) | 400 | `TYPE_MISMATCH` | sí, por parámetro |
| `ObjectOptimisticLockingFailureException` (update concurrente) | 409 | `CONCURRENT_MODIFICATION` | no |
| Cualquier otra `Exception` | 500 | `INTERNAL_SERVER_ERROR` | no |

Las excepciones de dominio viven en `com.logitrack.shipment.exception` y todas
extienden `DomainException`, que expone el `code()` que usa el handler. Las reglas de
negocio parten de `BusinessRuleException` (422): las derivadas solo aportan su `code`
y su mensaje. El handler 500 **no expone stacktrace ni detalles internos**: el error
completo se registra con `log.error` y el cliente recibe un mensaje genérico.

Los tests cubren cada fila de la tabla con un controller de prueba que lanza la
excepción correspondiente:

```bash
./mvnw test -Dtest=GlobalExceptionHandlerTest
```

## Envíos

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/shipments` | 201, Location y envío con tarifa e historial inicial |
| GET | `/api/v1/shipments/{id}` | Detalle por UUID |
| GET | `/api/v1/shipments/tracking/{trackingCode}` | Detalle por seguimiento |
| GET | `/api/v1/shipments?status=PENDIENTE&page=0&size=20` | Página, con estado opcional |
| PUT | `/api/v1/shipments/{id}/courier` | Asigna repartidor validando disponibilidad vía OpenFeign |
| PATCH | `/api/v1/shipments/{id}/status` | Cambia estado (valida repartidor activo en `EN_RUTA` vía OpenFeign) |

La paginación empieza en 0, admite entre 1 y 100 elementos y ordena por fecha
de creación descendente y UUID. La respuesta contiene `content`, `page`, `size`,
`totalElements` y `totalPages`. Los datos inválidos devuelven 400; un recurso
inexistente, 404; un conflicto de persistencia, 409. Los errores incluyen
`timestamp`, `status`, `error`, `code`, `message`, `path` y `details`.

El tracking usa SecureRandom (`LT-` y diez caracteres alfanuméricos mayúsculos),
preconsulta de existencia e índice único en BD. Cada intento de alta es una
transacción independiente; se reintenta únicamente una colisión de tracking y
se aborta después de cinco intentos. Envío e historial se confirman juntos.

## Descubrimiento centralizado (#78)

El perfil `prod,sprint3` de config-repo activa Eureka y deja vacia
`logitrack.courier.base-url`; Feign resuelve `courier-service` por nombre.
En Compose se anuncian `SERVICE_HOST=shipment-service` y el registro interno
`EUREKA_SERVER_URL=http://eureka-server:8065/eureka/`.
Los perfiles anteriores conservan la opcion `EUREKA_CLIENT_ENABLED=false`.
Esta rama parte del trabajo Feign de #84-#86; no reimplementa esos cambios.
