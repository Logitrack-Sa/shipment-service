# LogiTrack · shipment-service

[![CI](https://github.com/Logitrack-Sa/shipment-service/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Logitrack-Sa/shipment-service/actions/workflows/ci.yml)

API de gestión de envíos. Java 25, Spring Boot 3.5 y Maven Wrapper.

## Desarrollo

1. Instalar JDK 25 y configurar `JAVA_HOME`.
2. Abrir `pom.xml` en IntelliJ IDEA como proyecto Maven; seleccionar JDK 25.
3. Configurar `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`
   según `.env.example`. Spring no carga `.env` automáticamente: definir las
   variables en la configuración de ejecución del IDE o en el entorno.
4. Con MySQL disponible: `./mvnw spring-boot:run` (`.\mvnw.cmd` en Windows).

La aplicación expone `/actuator/health`, `/swagger-ui.html` y `/v3/api-docs`.
Hibernate valida el esquema; las migraciones de MySQL se entregan en #27.
La configuración bancaria del ejemplo anterior se retiró del servicio de envíos.

El Compose actual levanta solo MySQL 8.4: copiar `.env.example` a `.env`,
elegir contraseñas locales y ejecutar `docker compose up -d mysql`.
La imagen de la aplicación y el Compose completo se implementan en #26.

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

La suite unitaria puede ejecutarse sin BD ni red, con las dependencias ya
descargadas: `./mvnw -o -Dtest=ShipmentServiceTest,FeeCalculatorTest test`.

## Convenciones

- Paquete raíz: `com.logitrack.shipment`. Capas: `controller`, `service`,
  `repository`, `entity`, `dto`, `mapper`, `exception` y `config`.
- Clases y paquetes en inglés; estados del negocio en español.
- Endpoints bajo `/api/v1`, con sustantivos plurales.
- DTOs inmutables, validación de entrada y mappers manuales breves. La API no
  expone entidades JPA ni `Page` de Spring.
- Inyección por constructor; transacciones en servicios; lecturas con
  `readOnly = true`; dinero con `BigDecimal`.
- Ramas `feature/<issue>-descripcion`, `chore/...`, `test/...` o `docs/...`.
- Commits Conventional Commits breves, con descripción en español.
- Cambios mediante PR con `Closes #N` y aprobación de otro integrante.
  Nadie publica directamente en `main`.

## Modelo y decisiones

Las entidades usan UUID y auditoría compartida. `courier` es opcional hasta la
asignación; los cambios de estado requieren historial persistido. Peso y medidas
usan `BigDecimal` para evitar errores binarios en el cálculo de tarifa.

El documento `doc/Issue1.md` corresponde al ejemplo anterior; el alcance vigente
está en las issues #12–#33 de GitHub.

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

## Clientes

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/customers` | 201 Created con header `Location` y datos del cliente |
| GET | `/api/v1/customers/{id}` | 200 OK con detalle por UUID o 404 Not Found |
| GET | `/api/v1/customers?page=0&size=20` | 200 OK con página de clientes |

La creación valida unicidad de `email` (409 Conflict o 400 Bad Request si los datos son 
inválidos) y requiere `name` y `phone`. La lista paginada admite parámetros `page` y `size`.

## Envíos

| Método | Ruta | Resultado |
| --- | --- | --- |
| POST | `/api/v1/shipments` | 201, Location y envío con tarifa e historial inicial |
| GET | `/api/v1/shipments/{id}` | Detalle por UUID |
| GET | `/api/v1/shipments/tracking/{trackingCode}` | Detalle por seguimiento |
| GET | `/api/v1/shipments?status=PENDIENTE&page=0&size=20` | Página, con estado opcional |

La paginación empieza en 0, admite entre 1 y 100 elementos y ordena por fecha
de creación descendente y UUID. La respuesta contiene `content`, `page`, `size`,
`totalElements` y `totalPages`. Los datos inválidos devuelven 400; un recurso
inexistente, 404; un conflicto de persistencia, 409. Los errores incluyen
`timestamp`, `status`, `error`, `code`, `message`, `path` y `details`.

El tracking usa SecureRandom (`LT-` y diez caracteres alfanuméricos mayúsculos),
preconsulta de existencia e índice único en BD. Cada intento de alta es una
transacción independiente; se reintenta únicamente una colisión de tracking y
se aborta después de cinco intentos. Envío e historial se confirman juntos.

Las entidades básicas de cliente/repartidor, el contrato de errores y el
recorder de historial permiten integrar los envíos. Las APIs de clientes y
repartidores (#14/#15), los errores de estados (#17) y la consulta del historial
(#21) continúan en sus issues correspondientes.
