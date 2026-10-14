# Sprint 1: TP5 (Lógica de negocio avanzada, eventos y automatización)

Equipo: Alex, Gonzalo y Damian · Desarrollo y Arquitecturas Avanzadas de Software · Período lectivo 2026

## Supuestos y plan general

Se asume Spring Boot (por `app.comisiones.*` y la expresión Cron), con Java y JPA. Si usan otro stack, adapten los detalles técnicos. Se propone un sprint de 2 semanas.
## Horario de reunion:
Viernes 10 AM
## Horario de realizacion de Video para el Sprint:
Sabado 16pm
## Reparto

| Persona | Foco | Issues |
| --- | --- | --- |
| **Alex** | Bloque A: grupo familiar y límites | #2, #3, #4 |
| **Gonzalo** | Bloque B: alta, token y email | #5, #6, #7 |
| **Damian** | Config global, activación y Bloque C: comisiones | #1, #8, #9, #10 |
| **Todos** | Cierre | #11 |

**Orden sugerido**

- **Días 1-3:** #1 (Damian) y #2, #5 (Alex y Gonzalo) en paralelo, porque son la base.
- **Días 4-9:** #3, #4, #6, #7, #8, #9.
- **Días 10-12:** #10, que depende de #9, y luego #11 con pruebas integradas y merge.

**Labels sugeridos:** `bloque-A`, `bloque-B`, `bloque-C`, `config`, `backend`, `testing`, `sprint-1`. Milestone: `Sprint 1 - TP5`.

## Issue #1: Parámetros globales configurables (límites y comisiones)

**Asignado:** Damian | **Labels:** `config`, `backend` | **Estimación:** 2 pts

**Descripción:** Centralizar en `application.properties` y exponer vía `@ConfigurationProperties` todos los parámetros globales del TP, para que ningún valor quede hardcodeado.

**Tareas**

- [ ] Definir las propiedades:
  - `app.limites.extraccion-diaria.titular=100000.00`
  - `app.limites.extraccion-diaria.adherente=70000.00`
  - `app.comisiones.cuenta-corriente=5000.00`
  - `app.comisiones.caja-ahorro=2000.00`
  - `app.comisiones.cron=0 0 0 1 * *`, configurable para pruebas
  - `app.activacion.token-validez-horas=24`
- [ ] Crear las clases `LimitesProperties` y `ComisionesProperties` con `BigDecimal`, nunca `double`.
- [ ] Validar al arrancar con `@Validated`: valores no nulos y mayores o iguales a 0.
- [ ] Documentar las propiedades en el README.

**Criterios de aceptación**

- Cambiar un valor en el properties cambia el comportamiento sin tocar código.
- La app falla al arrancar si falta una propiedad obligatoria.

**Dependencias:** ninguna. Bloquea #4, #5 y #10.

## Issue #2: Entidad Adherente y vinculación al titular

**Asignado:** Alex | **Labels:** `bloque-A`, `backend` | **Estimación:** 5 pts

**Descripción:** Extender el modelo para que un Cliente titular pueda tener adherentes (cónyuge e hijos) asociados a su cuenta.

**Tareas**

- [ ] Crear la entidad `Adherente`: id, datos personales (nombre, apellido, DNI, email), `parentesco` (enum `CONYUGE`, `HIJO`), relación `@ManyToOne` con el `Cliente` titular.
- [ ] Decidir y documentar si el adherente es una entidad aparte o un `Cliente` con rol. Acordarlo con el equipo antes de codear.
- [ ] Repository, service y DTOs de request/response.
- [ ] Endpoints:
  - `POST /api/v1/clientes/{id}/adherentes`
  - `GET /api/v1/clientes/{id}/adherentes`
  - `DELETE /api/v1/clientes/{id}/adherentes/{adherenteId}`
- [ ] Validaciones: el titular debe existir y estar ACTIVO, no se permiten DNI duplicados y el parentesco es obligatorio.
- [ ] Migración de BD, si usan Flyway o Liquibase.

**Criterios de aceptación**

- Se puede vincular y desvincular un adherente a un titular.
- Un adherente siempre queda ligado a una única cuenta de titular.
- Tests unitarios del service.

**Dependencias:** ninguna. Bloquea #3 y #4.

## Issue #3: Restringir operatividad de adherentes (solo extracciones)

**Asignado:** Alex | **Labels:** `bloque-A`, `backend` | **Estimación:** 3 pts

**Descripción:** Los adherentes solo pueden hacer `EXTRACCION` sobre la cuenta del titular. Cualquier otra operación debe ser rechazada.

**Tareas**

- [ ] Identificar al ejecutante de cada operación (titular o adherente) en el request o en el contexto de seguridad.
- [ ] En el service de transacciones, validar que si el ejecutante es adherente, el tipo sea `EXTRACCION`.
- [ ] Rechazar depósito, transferencia, etc. con `403 Forbidden` y un mensaje claro.
- [ ] Validar que el adherente pertenezca al titular dueño de la cuenta.
- [ ] Tests: adherente extrae (OK), adherente deposita (403), adherente de otro titular (403).

**Criterios de aceptación**

- Un adherente nunca puede ejecutar una operación distinta de extracción.
- Las operaciones del titular no cambian.

**Dependencias:** #2.

## Issue #4: Validación de tope diario acumulado de extracciones

**Asignado:** Alex | **Labels:** `bloque-A`, `backend` | **Estimación:** 5 pts

**Descripción:** Antes de procesar una extracción, validar que la suma de extracciones del día del usuario más el monto solicitado no supere su tope. Titular: $100.000. Adherente: $70.000. Ambos topes se leen de la config global (#1).

**Tareas**

- [ ] Query en `TransaccionRepository`: suma de extracciones `COMPLETADA` del usuario (titular o adherente) entre `00:00` y `23:59:59` de hoy.
- [ ] Definir si "el día" se calcula en zona horaria `America/Argentina/Buenos_Aires` y documentarlo.
- [ ] El acumulado es **por usuario**: titular y adherentes tienen acumulados independientes.
- [ ] Lanzar `LimiteDiarioExcedidoException` con `422` o `400`, indicando el límite, lo acumulado y lo disponible.
- [ ] Ejecutar la validación antes de impactar el saldo.
- [ ] Tests de borde: justo en el tope (permitido), tope + $1 (rechazado), extracciones rechazadas que no suman, día nuevo que reinicia el acumulado.

**Criterios de aceptación**

- No se puede superar el tope diario con una o con varias extracciones.
- Los límites cambian solo editando la configuración.

**Dependencias:** #1, #2.

## Issue #5: Estado PENDIENTE\_ACTIVACION y Token de Activación

**Asignado:** Gonzalo | **Labels:** `bloque-B`, `backend` | **Estimación:** 5 pts

**Descripción:** Todo cliente nuevo nace en `PENDIENTE_ACTIVACION` y recibe un token UUID válido por 24 horas.

**Tareas**

- [ ] Agregar `PENDIENTE_ACTIVACION` al enum de estados del Cliente (junto con `ACTIVO`).
- [ ] Crear la entidad `TokenActivacion`: `id`, `token` (UUID único), `cliente` (`@ManyToOne`), `fechaCreacion`, `fechaExpiracion` (creación + 24 h), `usado` (boolean).
- [ ] `TokenActivacionRepository.findByToken`.
- [ ] En el alta de cliente: estado inicial `PENDIENTE_ACTIVACION`, generar el token y persistir todo en la misma transacción.
- [ ] La validez sale de `app.activacion.token-validez-horas`.
- [ ] Migración de BD.
- [ ] Tests del service de alta.

**Criterios de aceptación**

- Un cliente recién creado queda `PENDIENTE_ACTIVACION`, con un token y expiración a +24 h.

**Dependencias:** #1 (solo para la propiedad de validez). Bloquea #6 y #8.

## Issue #6: Evento de dominio asíncrono de alta de cliente

**Asignado:** Gonzalo | **Labels:** `bloque-B`, `backend` | **Estimación:** 3 pts

**Descripción:** Al registrar un cliente se publica un evento de dominio. Un listener asíncrono lo consume, para no bloquear el alta.

**Tareas**

- [ ] Crear `ClienteRegistradoEvent` con `clienteId`, `email`, `nombre` y `token`.
- [ ] Publicarlo con `ApplicationEventPublisher` desde el service de alta.
- [ ] Habilitar `@EnableAsync` y configurar un `TaskExecutor` propio.
- [ ] Listener con `@Async` y `@TransactionalEventListener(phase = AFTER_COMMIT)`, para que el email salga solo si el alta se confirmó.
- [ ] Manejo de errores: si falla el envío, loguear y no revertir el alta.
- [ ] Test: el evento se publica al crear un cliente y el listener se invoca.

**Criterios de aceptación**

- El alta responde sin esperar el envío del email.
- Un fallo del email no impide ni revierte el registro.

**Dependencias:** #5. Bloquea #7.

## Issue #7: Envío de email HTML de bienvenida con botón de activación

**Asignado:** Gonzalo | **Labels:** `bloque-B`, `backend` | **Estimación:** 5 pts

**Descripción:** El listener de #6 envía un correo HTML con datos de bienvenida y un enlace/botón de activación.

**Tareas**

- [ ] Agregar `spring-boot-starter-mail` y configurar SMTP por variables de entorno. Para desarrollo, usar Mailtrap, MailHog o Gmail con app password. **No commitear credenciales.**
- [ ] Plantilla HTML (Thymeleaf o similar) con nombre del cliente, mensaje de bienvenida, botón "Activar mi cuenta" que apunta a `{app.base-url}/api/v1/clientes/activar?token={uuid}`, y aviso de que el enlace vence en 24 h.
- [ ] Servicio `EmailService` usando `MimeMessage` con `setText(html, true)`.
- [ ] Propiedad `app.base-url` configurable.
- [ ] Probar el envío real a un inbox de prueba y adjuntar captura al PR.

**Criterios de aceptación**

- El email llega en formato HTML con el botón funcional.
- El enlace contiene el token correcto del cliente.

**Dependencias:** #6.

## Issue #8: Endpoint de activación GET /api/v1/clientes/activar?token=...

**Asignado:** Damian | **Labels:** `bloque-B`, `backend` | **Estimación:** 3 pts

**Descripción:** Endpoint que valida el token y activa al cliente.

**Tareas**

- [ ] Controller `GET /api/v1/clientes/activar` con `@RequestParam String token`.
- [ ] Lógica en el service:
  - Token inexistente → `400 Bad Request`.
  - Token expirado → `400 Bad Request`.
  - Token ya usado → `400 Bad Request`.
  - Token válido → cliente pasa a `ACTIVO` y el token queda marcado como usado.
- [ ] Mensajes de error distintos pero con el mismo código 400, con un `@ControllerAdvice` consistente con el resto del proyecto.
- [ ] Transaccional, y endpoint público si hay Spring Security.
- [ ] Tests: token válido, expirado, inexistente y reusado.

**Criterios de aceptación**

- Solo con token válido y vigente el cliente pasa a `ACTIVO`.
- Todos los casos inválidos devuelven 400.

**Dependencias:** #5. Coordinar con Gonzalo el contrato de `TokenActivacion` el día 1.

## Issue #9: Tipo de transacción DEBITO\_COMISION y servicio de cobro individual

**Asignado:** Damian | **Labels:** `bloque-C`, `backend` | **Estimación:** 3 pts

**Descripción:** Preparar la pieza que cobra la comisión a una cuenta, para que el scheduler (#10) solo la invoque.

**Tareas**

- [ ] Agregar `DEBITO_COMISION` al enum de tipos de `Transaccion`.
- [ ] Método `ComisionService.cobrarComision(Cuenta)`:
  - Determina el monto según el tipo de cuenta (Cuenta Corriente o Caja de Ahorro) desde `ComisionesProperties`.
  - Descuenta el saldo.
  - Crea la `Transaccion` `DEBITO_COMISION` con estado `COMPLETADA`.
- [ ] Definir el comportamiento con saldo insuficiente. Propuesta: la cuenta corriente puede quedar en negativo si ya lo permite el modelo, y la caja de ahorro registra el caso sin descontar. **Acordar con el equipo y documentar la decisión.**
- [ ] Una transacción de BD por cuenta (`REQUIRES_NEW`), para que el fallo de una no afecte al resto.
- [ ] Tests con ambos tipos de cuenta.

**Criterios de aceptación**

- Cada cobro deja un registro `DEBITO_COMISION` `COMPLETADA` con el monto correcto.
- El saldo final es consistente.

**Dependencias:** #1. Bloquea #10.

## Issue #10: Tarea programada de liquidación mensual de comisiones

**Asignado:** Damian | **Labels:** `bloque-C`, `backend` | **Estimación:** 5 pts

**Descripción:** Proceso automático que el día 1 de cada mes debita la comisión de todas las cuentas activas.

**Tareas**

- [ ] Habilitar `@EnableScheduling`.
- [ ] `LiquidacionComisionesJob` con `@Scheduled(cron = "${app.comisiones.cron}", zone = "America/Argentina/Buenos_Aires")`.
- [ ] Obtener todas las cuentas en estado `ACTIVA` y, **paginando** si son muchas, llamar a `cobrarComision` para cada una.
- [ ] Capturar excepciones por cuenta, para que un error no corte el lote, y loguear un resumen final: procesadas, fallidas y monto total.
- [ ] Evitar doble ejecución del mismo período, por ejemplo con una validación por mes y cuenta.
- [ ] Cron por defecto: `0 0 0 1 * *`. Para pruebas, `0 * * * * *` (cada minuto) vía propiedad.
- [ ] Tests de integración que invoquen el método directamente, sin esperar al cron: cuentas ACTIVA reciben comisión y las inactivas no.

**Criterios de aceptación**

- Solo las cuentas `ACTIVA` reciben el débito, según su tipo.
- Cambiar el cron no requiere tocar código.
- El fallo de una cuenta no frena a las demás.

**Dependencias:** #9.

## Issue #11: Pruebas integradas, documentación y cierre del sprint

**Asignados:** Alex, Gonzalo y Damian (cada uno su bloque) | **Labels:** `testing`, `docs` | **Estimación:** 3 pts

**Tareas**

- [ ] Alex: colección Postman o requests `.http` de adherentes y límites, y escenario end-to-end de extracciones.
- [ ] Gonzalo: escenario end-to-end de alta, email y activación.
- [ ] Damian: README con todas las propiedades de config y cómo probar el scheduler.
- [ ] Revisión cruzada de PRs: Alex revisa a Gonzalo, Gonzalo a Damian y Damian a Alex.
- [ ] Merge a `main`, demo y retrospectiva.

**Criterios de aceptación**

- Todos los criterios de las issues anteriores se verifican en un entorno limpio.
- El README permite a cualquiera levantar y probar el proyecto.

## Reglas de trabajo sugeridas

- Una rama por issue: `feature/#N-descripcion-corta`.
- Los PRs usan `Closes #N`, y cada uno necesita al menos 1 aprobación de otro integrante.
- Branch protection en `main`.
- Daily corta por chat y revisión de bloqueos cada 2-3 días.
