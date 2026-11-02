# Validación de Comunicación OpenFeign y Resiliencia (`shipment-service` ↔ `courier-service`)

Este documento describe las pruebas automatizadas implementadas para el **Issue #86 (`[SPRINT 3][TESTING] Validar comunicación Feign entre microservicios`)**, cómo ejecutarlas sin depender de direcciones IP fijas ni servicios externos en ejecución, y los resultados obtenidos.

---

## 1. Arquitectura de las pruebas

La validación se divide en dos niveles complementarios:

1. **Pruebas de Integración de extremo a extremo (`FeignCourierCommunicationIntegrationTest`)**:
   - Levantan el contexto completo de Spring Boot (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`).
   - Verifican que `CourierProperties.baseUrl()` sea `null` y que `CourierFeignClient` resuelva el destino utilizando únicamente el nombre lógico **`courier-service`** a través de **Spring Cloud LoadBalancer** (`ServiceInstanceListSupplier`, la misma abstracción que alimenta el cliente de **Eureka** en producción).
   - Evalúan el flujo real desde el controlador REST (`ShipmentController`) pasando por `ShipmentService`, `FeignCourierLookup`, `CourierFeignClient`, `CourierErrorDecoder`, `CourierFeignFallbackFactory` y `GlobalExceptionHandler`.
2. **Pruebas Unitarias del Cliente y Excepciones (`HttpCourierLookupTest` y `GlobalExceptionHandlerTest`)**:
   - Validan la propagación exclusiva de tokens JWT autenticados (`Authorization: Bearer`), rechazo de payloads malformados o inconsistentes, decodificación de errores HTTP (`401`, `403`, `404`, `422`, `500`, `503`, `302`) y preservación de la causa técnica en el fallback.

---

## 2. Instrucciones para ejecutar las pruebas

No se requiere tener MySQL, Config Server, Eureka Server ni `courier-service` levantados para ejecutar la suite de validación Feign:

```powershell
# Ejecutar únicamente las suites de comunicación Feign, asignación y manejo de errores
.\mvnw.cmd test -Dtest="FeignCourierCommunicationIntegrationTest,HttpCourierLookupTest,AssignCourierTest,GlobalExceptionHandlerTest"

# Ejecutar la suite completa del microservicio
.\mvnw.cmd clean test
```

---

## 3. Matriz de escenarios validados y resultados

| # | Escenario requerido (Issue #86) | Test automatizado | Respuesta de `courier-service` simulada | Resultado en `shipment-service` | Estado |
|---|---|---|---|---|---|
| 1 | **Consulta exitosa del estado del repartidor** | `FeignCourierCommunicationIntegrationTest#queriesCourierStatusAndTransitionsShipmentToEnRutaSuccessfully` | `200 OK` (`{"courierId":"...","status":"ACTIVO"}`) en `GET /api/v1/internal/couriers/{id}/status` | `200 OK` — El envío pasa a estado `EN_RUTA` y se registra en el historial. | ✅ Aprobado |
| 2 | **Asignación de envío con repartidor disponible** | `FeignCourierCommunicationIntegrationTest#assignsShipmentWhenCourierIsAvailableViaFeignAndServiceDiscovery` | `200 OK` (`{"courierId":"...","status":"ACTIVO","available":true}`) en `GET /api/v1/couriers/{id}/availability` | `200 OK` — El envío en estado `PENDIENTE` queda asignado al `courierId`. | ✅ Aprobado |
| 3 | **Repartidor no existe (`HTTP 404`)** | `FeignCourierCommunicationIntegrationTest#returnsControlled404WhenCourierDoesNotExistAndDoesNotAssign` | `404 Not Found` (`{"code":"RESOURCE_NOT_FOUND"}`) | `404 Not Found` (`RESOURCE_NOT_FOUND`) — `CourierErrorDecoder` traduce a `ResourceNotFoundException`; el envío no se modifica. | ✅ Aprobado |
| 4 | **Error interno en `courier-service` (`HTTP 500`)** | `FeignCourierCommunicationIntegrationTest#returnsControlled503WhenCourierServiceRespondsHttp500AndPreservesState` | `500 Internal Server Error` | `503 Service Unavailable` (`COURIER_UNAVAILABLE`) — Respuesta estructurada `ErrorResponse`; no se persiste la asignación. | ✅ Aprobado |
| 5 | **`courier-service` detenido o no responde (Conexión rechazada / Timeout)** | `FeignCourierCommunicationIntegrationTest#activatesFallbackAndReturnsControlled503WhenCourierServiceIsStoppedOrTimesOut` | Puerto cerrado (`ConnectException`) y demora superior a `read-timeout` (`RetryableException`) | `503 Service Unavailable` (`COURIER_UNAVAILABLE`) — Se activa `CourierFeignFallbackFactory` y se aborta la asignación. | ✅ Aprobado |
| 6 | **Resolución dinámica sin IP fija** | `FeignCourierCommunicationIntegrationTest#doesNotDependOnFixedBaseUrlAndUsesLogicalServiceId` | Instancia resuelta vía `ServiceInstanceListSupplier` para `courier-service` | `courierProperties.baseUrl() == null` y `serviceId == "courier-service"`. | ✅ Aprobado |
| 7 | **Repartidor inactivo (`200` con `available=false` o `422`)** | `HttpCourierLookupTest#rejectsInactiveWithoutRetry` y `translates422` | `200 OK` (`available: false`) / `422 Unprocessable Entity` (`COURIER_NOT_ACTIVE`) | `422 Unprocessable Entity` (`COURIER_NOT_ACTIVE`). | ✅ Aprobado |

---

## 4. Ejemplo de respuesta controlada de error (`ErrorResponse`)

Cuando `courier-service` devuelve `HTTP 500`, excede el timeout o se encuentra detenido, `shipment-service` responde con estado `503 Service Unavailable` manteniendo el contrato uniforme:

```json
{
  "timestamp": "2026-11-01T03:05:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "COURIER_UNAVAILABLE",
  "message": "No se pudo validar el repartidor en el servicio remoto",
  "path": "/api/v1/shipments/328e07b1-250d-4a0d-a45a-36c9a5083d6d/courier"
}
```
