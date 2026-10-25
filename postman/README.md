# LogiTrack · Colección Postman y Entorno (Sprint 2)

Colección exportable de Postman v2.1 y entorno de variables para el **Sprint 2** del ecosistema de microservicios **LogiTrack** ([Issue #63 · S2-19](https://github.com/Logitrack-Sa/shipment-service/issues/63)).

Esta entrega cubre la interacción distribuida entre los microservicios del sistema, la autenticación y autorización mediante tokens JWT RS256, escenarios ordenados por rol (**ADMIN**, **CLIENTE**, **REPARTIDOR**) y una batería completa de **casos negativos y validaciones de seguridad**.

---

## 1. Contenido del Directorio

* **`LogiTrack_Sprint2.postman_collection.json`**: Colección con 52 solicitudes agrupadas en 6 carpetas temáticas con scripts automáticos de pre-request y tests de aserción (`pm.test`).
* **`LogiTrack_Sprint2_Local.postman_environment.json`**: Entorno de variables para ejecución local contra los puertos estándar de desarrollo. No incluye contraseñas reales ni tokens productivos.
* **`README.md`**: Guía operativa, orden del Runner y tabla comparativa frente al Sprint 1.

---

## 2. Diferencias clave con la colección del Sprint 1

| Aspecto | Colección Sprint 1 | Colección Sprint 2 (Actual) |
|---|---|---|
| **Topología y Puertos** | Un solo servicio monolítico (`localhost:8080`). | Tres microservicios independientes: `shipment-service` (:8081), `courier-service` (:8082) y `security-service` (:8083). |
| **Seguridad y Autenticación** | Endpoints públicos sin cabeceras de autorización. | Autenticación centralizada JWT RS256. Cada petición lleva `Authorization: Bearer <token>`. |
| **Gestión de Roles** | No existía concepto de rol en las llamadas HTTP. | Matriz estricta de roles: `ADMIN`, `CLIENTE` y `REPARTIDOR`. |
| **Pertenencia de Recursos** | Cualquier cliente podía consultar o alterar cualquier recurso. | Aislamiento por sujeto: un `CLIENTE` solo puede crear y ver envíos con su propio `customerId`. Un `REPARTIDOR` solo ve envíos asignados y su propio estado. |
| **Desacoplamiento de Repartidores** | Repartidores gestionados dentro del mismo `shipment-service` con claves foráneas JPA directas. | `courier-service` maneja su propia base de datos MySQL. Envíos guarda únicamente la referencia UUID escalar. Validación de estado y disponibilidad vía endpoints REST dedicados. |
| **Escenarios Negativos** | Pruebas de validación básicas (400) y de regla de negocio local. | Batería integral: 401 (sin token, manipulado, login inválido), 403 (rol prohibido, escalación en registro), 403/404 (acceso ajeno) y 422 (máquina de estados, courier inactivo, cancelación indebida). |
| **Automatización del Runner** | Requiere reinicio manual de base de datos por colisiones de email/tracking. | Generación de prefijos únicos dinámicos (`run_id`, `Date.now()`) y script de reset para ejecuciones infinitas e idempotentes. |

---

## 3. Mapeo de Variables y Puertos del Entorno

El archivo `LogiTrack_Sprint2_Local.postman_environment.json` define las siguientes variables base:

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `shipment_service_url` | `http://localhost:8081` | Microservicio de envíos, clientes y auditoría de estados. |
| `courier_service_url` | `http://localhost:8082` | Microservicio de repartidores y disponibilidad. |
| `security_service_url` | `http://localhost:8083` | Microservicio de autenticación, usuarios y emisión de JWT. |
| `config_server_url` | `http://localhost:8888` | Servidor central de configuración (Spring Cloud Config). |
| `admin_token` | *(generado dinámicamente)* | Token JWT asignado al rol `ADMIN` tras el login. |
| `cliente_token` | *(generado dinámicamente)* | Token JWT asignado al rol `CLIENTE` tras el login. |
| `repartidor_token` | *(generado dinámicamente)* | Token JWT asignado al rol `REPARTIDOR` tras el login. |
| `customer_id` | *(capturado en runtime)* | UUID del cliente creado en el dominio para asociar a los envíos. |
| `courier_id_activo` | *(capturado en runtime)* | UUID del repartidor en estado `ACTIVO` disponible para asignaciones. |
| `courier_id_inactivo` | *(capturado en runtime)* | UUID de repartidor en estado `INACTIVO` utilizado para pruebas de rechazo (422). |

> **Nota de seguridad:** Por criterio de aceptación, los archivos exportados se entregan con las variables de tokens vacías y credenciales genéricas de entorno de desarrollo (`admin123`, `Cliente123!`, `Repartidor123!`). Nunca se versionan secretos ni claves privadas en el repositorio.

---

## 4. Estructura de Carpetas y Flujo de la Colección

La colección está dividida en 6 secciones diseñadas para ejecutarse secuencialmente:

```
LogiTrack - Sprint 2 Microservicios/
├── 00. Setup & Reset
│   └── 00.1 Inicializar y Resetear Variables del Runner
├── 01. Auth
│   ├── 01.1 Registro Público (POST /api/v1/auth/register) -> Rol CLIENTE
│   ├── 01.2 Login ADMIN (POST /api/v1/auth/login) -> Captura admin_token
│   ├── 01.3 Login CLIENTE (POST /api/v1/auth/login) -> Captura cliente_token
│   ├── 01.4 Login REPARTIDOR (POST /api/v1/auth/login) -> Captura repartidor_token
│   ├── 01.5 Consultar Perfil Propio (GET /api/v1/auth/me) - Rol ADMIN
│   ├── 01.6 Consultar Perfil Propio (GET /api/v1/auth/me) - Rol CLIENTE
│   └── 01.7 Consultar Perfil Propio (GET /api/v1/auth/me) - Rol REPARTIDOR
├── 02. ADMIN
│   ├── 02.1 Clientes
│   │   ├── 02.1.1 Crear Cliente en Dominio (POST /api/v1/customers)
│   │   ├── 02.1.2 Listar Clientes (GET /api/v1/customers)
│   │   └── 02.1.3 Obtener Cliente por ID (GET /api/v1/customers/{id})
│   ├── 02.2 Repartidores
│   │   ├── 02.2.1 Crear Repartidor Principal (POST /api/v1/couriers) -> ACTIVO
│   │   ├── 02.2.2 Crear Repartidor Secundario (POST /api/v1/couriers)
│   │   ├── 02.2.3 Desactivar Repartidor Secundario (PATCH /api/v1/couriers/{id}/status) -> INACTIVO
│   │   ├── 02.2.4 Listar Repartidores (GET /api/v1/couriers)
│   │   ├── 02.2.5 Obtener Repartidor por ID (GET /api/v1/couriers/{id})
│   │   ├── 02.2.6 Actualizar Repartidor (PUT /api/v1/couriers/{id})
│   │   └── 02.2.7 Consultar Disponibilidad de Repartidor (GET /api/v1/couriers/{id}/availability)
│   └── 02.3 Envíos
│       ├── 02.3.1 Crear Envío como ADMIN (POST /api/v1/shipments)
│       ├── 02.3.2 Crear Envío Auxiliar para Cancelación (POST /api/v1/shipments)
│       ├── 02.3.3 Listar Todos los Envíos (GET /api/v1/shipments?page=0&size=10)
│       ├── 02.3.4 Asignar Repartidor a Envío (PATCH /api/v1/shipments/{id}/courier)
│       └── 02.3.5 Cancelar Envío PENDIENTE (POST /api/v1/shipments/{id}/cancellation)
├── 03. CLIENTE
│   ├── 03.1 Crear Envío Propio (POST /api/v1/shipments)
│   ├── 03.2 Consultar Envío Propio por ID (GET /api/v1/shipments/{id})
│   ├── 03.3 Consultar Seguimiento de Envío Propio (GET /api/v1/shipments/tracking/{code})
│   ├── 03.4 Consultar Historial de Envío Propio (GET /api/v1/shipments/{id}/history)
│   └── 03.5 Listar Envíos Propios del Cliente (GET /api/v1/shipments)
├── 04. REPARTIDOR
│   ├── 04.1 Consultar Estado Interno Propio (GET /api/v1/internal/couriers/{id}/status)
│   ├── 04.2 Listar Envíos Asignados a mi Cargo (GET /api/v1/shipments?courierId=...)
│   ├── 04.3 Iniciar Envío a EN_RUTA (PATCH /api/v1/shipments/{id}/status)
│   ├── 04.4 Finalizar Envío a ENTREGADO (PATCH /api/v1/shipments/{id}/status)
│   └── 04.5 Consultar Historial Completo del Envío Entregado (GET /api/v1/shipments/{id}/history)
└── 05. Casos Negativos & Seguridad
    ├── 05.1 Autenticación (401)
    │   ├── 05.1.1 401 Sin Token en Endpoint Protegido (GET /api/v1/shipments)
    │   ├── 05.1.2 401 Token Inválido o Firma Manipulada (GET /api/v1/shipments)
    │   └── 05.1.3 401 Login con Credenciales Erróneas (POST /api/v1/auth/login)
    ├── 05.2 Autorización por Rol (403)
    │   ├── 05.2.1 403 CLIENTE intenta Crear Courier (POST /api/v1/couriers)
    │   ├── 05.2.2 403 CLIENTE intenta Consultar Disponibilidad (GET /api/v1/couriers/{id}/availability)
    │   ├── 05.2.3 403 CLIENTE intenta Asignar Courier (PATCH /api/v1/shipments/{id}/courier)
    │   ├── 05.2.4 403 CLIENTE intenta Cancelar Envío (POST /api/v1/shipments/{id}/cancellation)
    │   ├── 05.2.5 403 REPARTIDOR intenta Crear Cliente (POST /api/v1/customers)
    │   ├── 05.2.6 403 REPARTIDOR intenta Crear Envío (POST /api/v1/shipments)
    │   └── 05.2.7 400/403 Registro Público con Rol Privilegiado ADMIN (POST /api/v1/auth/register)
    ├── 05.3 Pertenencia y Acceso Ajeno (403/404)
    │   ├── 05.3.1 403/404 CLIENTE intenta Consultar Envío de Otro Cliente (GET /api/v1/shipments/{id})
    │   ├── 05.3.2 403 CLIENTE intenta Crear Envío con customerId Ajeno (POST /api/v1/shipments)
    │   └── 05.3.3 403 REPARTIDOR intenta Consultar Estado Interno de Otro Repartidor (GET /internal)
    └── 05.4 Reglas de Dominio y Máquina de Estados (422)
        ├── 05.4.1 422 Asignar Courier INACTIVO a Envío (PATCH /api/v1/shipments/{id}/courier)
        ├── 05.4.2 422 Transición a EN_RUTA sin Courier Asignado (PATCH /api/v1/shipments/{id}/status)
        ├── 05.4.3 422 Transición Inválida de PENDIENTE directo a ENTREGADO
        ├── 05.4.4 422 Cancelar Envío ya ENTREGADO
        ├── 05.4.5 422 Cancelar Envío ya CANCELADO
        └── 05.4.6 422 Modificar Estado de Envío Terminal CANCELADO
```

---

## 5. Instrucciones de Ejecución

### Opción A: Postman App (Collection Runner)

1. Abrir Postman e importar ambos archivos:
   * `File -> Import -> LogiTrack_Sprint2.postman_collection.json`
   * `File -> Import -> LogiTrack_Sprint2_Local.postman_environment.json`
2. Seleccionar el entorno activo: **LogiTrack Sprint 2 - Local** (en la esquina superior derecha).
3. Hacer clic derecho sobre la colección `LogiTrack - Sprint 2 Microservicios` y seleccionar **Run collection**.
4. En la configuración del Runner:
   * **Order of runs:** Mantener el orden de carpetas predefinido (`00` a `05`).
   * **Persist responses:** Opcional (desmarcar para no acumular estado entre corridas).
   * **Keep variable values:** Desmarcar para que cada corrida use las variables limpiadas por `00.1`.
5. Presionar **Run LogiTrack - Sprint 2 Microservicios**. Todas las aserciones deben terminar en verde (`PASS`).

### Opción B: Newman (Línea de comandos / CI)

Para ejecutar la colección completa de forma automatizada mediante consola:

```bash
# Instalación de Newman (si no está disponible globalmente)
npm install -g newman

# Ejecución contra los microservicios locales
newman run postman/LogiTrack_Sprint2.postman_collection.json \
  -e postman/LogiTrack_Sprint2_Local.postman_environment.json \
  --reporters cli
```

Para generar reportes HTML o JUnit para CI:
```bash
newman run postman/LogiTrack_Sprint2.postman_collection.json \
  -e postman/LogiTrack_Sprint2_Local.postman_environment.json \
  --reporters cli,junit \
  --reporter-junit-export target/newman-results.xml
```

---

## 6. Validación de Contratos y Códigos de Error

Todas las respuestas de error en la colección validan el contrato unificado `ErrorResponse` del sistema:

```json
{
  "timestamp": "2026-10-24T23:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "COURIER_NOT_ACTIVE",
  "message": "El repartidor asignado no se encuentra activo",
  "path": "/api/v1/shipments/...",
  "details": null
}
```

Las aserciones verifican tanto el código HTTP como el valor de `code`, garantizando que la aplicación responda conforme a las reglas arquitectónicas acordadas.
