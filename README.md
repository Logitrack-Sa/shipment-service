# shipment-service

Servicio Spring Boot (Java 17) del Trabajo Final - Sprint 1 (TP5). Implementa la lógica de
negocio bancaria del caso de estudio: clientes, cuentas, extracciones, comisiones y activación.

## Configuración global de parámetros (Issue #1)

Todos los parámetros globales del TP están centralizados en `src/main/resources/application.yaml`
y expuestos mediante clases tipadas con `@ConfigurationProperties`. Ningún servicio de negocio
debe repetir importes ni duraciones como constantes: inyectar las properties correspondientes.

### Propiedades

| Propiedad | Tipo | Unidad | Default | Descripción |
| --- | --- | --- | --- | --- |
| `app.limites.extraccion-diaria.titular` | `BigDecimal` | pesos | `100000.00` | Tope diario de extracciones de un cliente titular |
| `app.limites.extraccion-diaria.adherente` | `BigDecimal` | pesos | `70000.00` | Tope diario de extracciones de un adherente |
| `app.comisiones.cuenta-corriente` | `BigDecimal` | pesos | `5000.00` | Comisión mensual de una cuenta corriente |
| `app.comisiones.caja-ahorro` | `BigDecimal` | pesos | `2000.00` | Comisión mensual de una caja de ahorro |
| `app.comisiones.cron` | `String` | expresión cron Spring (6 campos, zona `America/Argentina/Buenos_Aires`) | `0 0 0 1 * *` | Horario de la liquidación mensual de comisiones |
| `app.activacion.token-validez-horas` | `Long` | horas | `24` | Validez del token de activación de cuenta |

Los importes usan `BigDecimal` (nunca `double`) y se validan al arranque: obligatorios,
no nulos y `>= 0`. La vigencia del token debe ser positiva y el cron debe ser una expresión
cron válida de Spring. Si falta o es inválida una propiedad obligatoria, **la aplicación no
arranca** y el error de binding identifica el campo exacto.

### Clases tipadas

| Clase | Prefijo | Consumidores previstos |
| --- | --- | --- |
| `LimitesProperties` | `app.limites` | Issue #4 - validación de tope diario de extracciones |
| `ComisionesProperties` | `app.comisiones` | Issues #9 y #10 - cobro y liquidación de comisiones |
| `ActivacionProperties` | `app.activacion` | Issue #5 - expiración del token de activación |

Ubicación: `com.logitrack.shipmentservice.config`. Registradas con `@ConfigurationPropertiesScan`
en `ShipmentServiceApplication`.

### Sobrescritura de valores

Cualquier propiedad puede sobrescribirse sin tocar código, por orden de precedencia
(mayor a menor):

1. **Variables de entorno** (mayúsculas, `_` en lugar de `.` y `-`):

   ```bash
   export APP_LIMITES_EXTRACCION_DIARIA_TITULAR=200000.00
   export APP_COMISIONES_CRON="0 * * * * *"
   export APP_ACTIVACION_TOKEN_VALIDEZ_HORAS=48
   ```

2. **Argumentos de línea de comando:**

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.comisiones.caja-ahorro=3500.00"
   ```

3. **Perfiles de Spring** (por ejemplo `src/main/resources/application-dev.yaml`):

   ```yaml
   # application-dev.yaml - para desarrollo y pruebas
   app:
     comisiones:
       cron: "0 * * * * *"   # liquidación cada minuto en vez de mensual
     activacion:
       token-validez-horas: 1
   ```

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

4. **Edición directa de `application.yaml`** (requiere commit; sirve para cambios de defaults).

### ¿Qué cambios requieren reiniciar la aplicación?

Todas estas propiedades se leen **una sola vez, al arrancar** el contexto de Spring
(binding de `@ConfigurationProperties`). Cualquier cambio -ya sea en `application.yaml`,
en un perfil, en variables de entorno o en argumentos- requiere **reiniciar la
aplicación** para surtir efecto. No hay refresh dinámico (no se usa Spring Cloud Config
ni actuator `/refresh`).

### Verificación

Los tests de `src/test/java/com/logitrack/shipmentservice/config/` cubren:

- Binding con los defaults de `application.yaml`.
- Binding con valores sobrescritos.
- Fallo de arranque ante propiedades ausentes o nulas.
- Fallo de arranque ante importes negativos, vigencia de token `<= 0` y cron inválido.

```bash
./mvnw test -Dtest='LimitesPropertiesTest,ComisionesPropertiesTest,ActivacionPropertiesTest'
```

## Correr la aplicación

```bash
./mvnw spring-boot:run
```

Levanta MySQL vía Docker Compose (`compose.yaml`) automáticamente con
`spring-boot-docker-compose`.
