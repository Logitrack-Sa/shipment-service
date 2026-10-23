# Contratos v1 comprobables (#53)

Los ejemplos ejecutables están en `src/test/resources/contracts/v1`. Son datos ficticios; los JWT se firman con un par RSA efímero creado durante los tests. No hay claves privadas versionadas. Los identificadores de usuario y de dominio son distintos.

## Permisos de shipment-service
| Método y ruta | ADMIN | CLIENTE | REPARTIDOR |
|---|---|---|---|
| POST /api/v1/shipments | Sí | customerId propio | No |
| GET /api/v1/shipments | Todos | Filtro customerId en SQL | Filtro courierId en SQL |
| GET /api/v1/shipments/{id} | Todos | Propio | Asignado |
| GET /api/v1/shipments/tracking/{trackingCode} | Todos | Propio | Asignado |
| GET /api/v1/shipments/{id}/history | Todos | Propio | Asignado |
| PUT /api/v1/shipments/{id}/courier | Sí | No | No |
| PATCH /api/v1/shipments/{id}/status | Máquina de estados | No | Asignado; EN_RUTA o ENTREGADO |
| POST /api/v1/shipments/{id}/cancellation | Solo PENDIENTE | No | No |
| /api/v1/customers/** | Sí | No | No |
| /api/v1/couriers/** | Fuera de este servicio | Fuera de este servicio | Fuera de este servicio |

Documentación y health son públicos: exponen contratos y estado mínimo, sin datos de clientes/envíos. El resto de Actuator y rutas no declaradas se deniegan. Recursos ajenos conocidos devuelven 403; inexistentes, 404. No se publican listados ni historial sin autorización. Las respuestas de seguridad siguen ErrorResponse (timestamp,status,error,code,message,path).

## HTTP entre servicios
Asignación: GET courier `/api/v1/couriers/{id}/availability`; EN_RUTA: GET `/api/v1/internal/couriers/{id}/status`. Se transmite exactamente el Bearer ya autenticado. El UUID devuelto debe coincidir; estado solo ACTIVO/INACTIVO; available debe ser coherente. Un payload inválido es fallo de dependencia (503).

404 → RESOURCE_NOT_FOUND; inactivo/422 → COURIER_NOT_ACTIVE (422); 401 → COURIER_UNAUTHORIZED (401); 403 → COURIER_FORBIDDEN (403); 5xx, respuesta inválida, conexión o timeout → COURIER_UNAVAILABLE (503). Ningún fallo cambia asignación, estado o historial. Sin reintentos automáticos. Límites de conexión/lectura configurables y positivos.

Auth, claims RS256 y variables base: [CONTRATOS.md](CONTRATOS.md). JWT exige sub UUID, kid, iat, exp, issuer, audience y roles conocidos. Una identidad sin vínculo no obtiene acceso de cliente/repartidor. Las claves de verificación se montan; la clave privada nunca se solicita a shipment. Rotación: desplegar clave pública y kid nuevos coordinadamente, luego emitir tokens nuevos; durante el corte los tokens de kid anterior se rechazan. No consultar al emisor por petición.

## Configuración estable
Spring Cloud 2025.0.x corresponde a Boot 3.5.x. Perfiles dev/prod usan Config Server obligatorio; test deshabilita el cliente. Bootstrap: application.name, profiles.active y config.import por entorno.

| Variable | Propiedad externa |
|---|---|
| DB_URL / DB_USERNAME / DB_PASSWORD | spring.datasource.url / username / password |
| COURIER_SERVICE_URL | logitrack.courier.base-url |
| JWT_PUBLIC_KEY_LOCATION | logitrack.security.public-key-location |
| JWT_KEY_ID | logitrack.security.key-id |
| JWT_ISSUER / JWT_AUDIENCE | logitrack.security.issuer / audience |

La contraseña y el PEM privado nunca se guardan en config-repo. Documentar las propiedades exactas de conexión/lectura en la entrega del cliente y mantener los valores HTTP solo en el Config Server. DB_USER queda como alias heredado para el arranque local del Sprint 1; los perfiles distribuidos usan DB_USERNAME.

Fuentes: [compatibilidad Spring Cloud](https://spring.io/projects/spring-cloud/), [JWT local](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Config Client](https://docs.spring.io/spring-cloud-config/reference/client.html).
