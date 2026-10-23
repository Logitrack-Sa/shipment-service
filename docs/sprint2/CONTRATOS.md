# Contratos iniciales del Sprint 2

Implementación de shipment: [contratos y fixtures v1](CONTRATOS-V1.md), [configuración](CONFIGURACION-SHIPMENT.md) y [migración](MIGRACION-COURIER.md).

Estado de los servicios externos: propuesta inicial operativa para trabajo paralelo. Cada propietario valida el contrato en su issue; cambios incompatibles se acuerdan por PR antes de integrar. No significa que el servicio esté implementado.

## Base verificada
shipment-service conserva Java25, Boot3.5.16, JPA, MySQL, Flyway, tarifa volumétrica, tracking, estados, cancelación y auditoría del Sprint1. Se extrae CourierController/Service/Repository/Mapper, Courier y sus DTOs; no se reescribe el dominio de envíos. No modificar migraciones ya aplicadas.

## Repositorios y puertos
| Repositorio | Responsabilidad | Puerto host/contenedor inicial | Persistencia |
|---|---|---|---|
| shipment-service | Envíos, clientes, tarifas, asignación, historial y entrega Compose | 8081 | MySQL shipment |
| courier-service | Repartidores, estado y disponibilidad | 8082 | MySQL courier |
| security-service | Usuarios, roles, login y JWT | 8083 | MySQL security |
| config-server | Configuración desde Git | 8888 | Git, no MySQL |
| config-repo | Propiedades dev/prod sin secretos | No es aplicación | Git |

No compartir tablas, conexiones ni entidades JPA. Sin API Gateway, broker ni frontend nuevo. Java25 requerido; elegir Spring Cloud compatible con Boot actual en su issue, sin suponer compatibilidad por número parecido.

## Courier REST v1
Se conserva /api/v1/couriers. ADMIN registra, lista, consulta, actualiza datos y cambia estado.
- POST /api/v1/couriers ->201. name,vehicleType; UUID generado por servidor.
- GET /api/v1/couriers y GET /{id} ->200. Conservar DTO existente: id,name,vehicleType,status,createdAt. Si se pagina, versionar/documentar forma de respuesta y actualizar consumidor.
- PUT /api/v1/couriers/{id} ->200. Solo name,vehicleType; sin id/auditoría/usuario/rol editables.
- PATCH /api/v1/couriers/{id}/status ->200. {"status":"ACTIVO"} o INACTIVO.
- GET /api/v1/couriers/{id}/availability ->200. {"courierId":"UUID","status":"ACTIVO","available":true}. Exclusivo ADMIN, se utiliza al asignar.
- GET /api/v1/internal/couriers/{id}/status ->200. {"courierId":"UUID","status":"ACTIVO"}. ADMIN o REPARTIDOR con courierId firmado igual al solicitado. Respuesta mínima para EN_RUTA; no habilita CRUD ni listados a repartidores.

Disponibilidad inicial = estado ACTIVO, como Sprint1; no supone exclusividad ni depende de consultar shipment. Si cambia la regla, acordarla explícitamente sin introducir dependencia circular. Shipment transmite su Bearer validado a courier. No introducir token técnico con permisos amplios para eludir autorización del usuario.

400 payload inválido,401 token ausente/inválido,403 sin permiso,404 courier inexistente. Shipment mantiene422 para asignación/transición inválida y devuelve503 cuando la dependencia falla o vence el timeout; no convierte errores de autenticación remotos en éxito. Asignar requiere envío PENDIENTE; EN_RUTA requiere courier asignado ACTIVO. Fallos no modifican envío ni historial.

## Identidad y JWT
- POST /api/v1/auth/register: público, crea exclusivamente CLIENTE; no aceptar role ADMIN/REPARTIDOR ni IDs ajenos elegidos por el solicitante.
- POST /api/v1/auth/login: credenciales -> accessToken,tokenType=Bearer,expiresIn. 401 genérico si inválidas.
- GET /api/v1/auth/me: identidad del sujeto validado, roles y asociaciones; sin hashes ni claves.
- Provisión ADMIN inicial por entorno seguro. Gestión de roles y asociación customerId/courierId solo por ADMIN. Documentar esos endpoints al implementar; no existe registro público privilegiado.

JWT RS256: sub=UUID usuario, roles=[ADMIN|CLIENTE|REPARTIDOR], iss=issuer configurable, aud incluye servicios autorizados, iat,exp,kid; customerId/courierId según asociación persistida y confiable. Son identificadores de dominio separados de sub: no asumir que coinciden. Vínculos únicos, configurados por ADMIN, verificados contra recursos locales al operar; no confiar en un campo del request/login para emitirlos. Si falta vínculo válido, denegar acceso a recursos hasta su provisión. Registrar un cliente de dominio no crea automáticamente una cuenta.

Clave privada solo en security-service por secreto/montaje; consumidores usan pública. Validar algoritmo, firma,issuer,audience,exp localmente sin consulta HTTP al emisor por petición. Claves de tests efímeras, nunca de producción. Configurar límites de vida y procedimiento de rotación/kid.

## Matriz mínima
| Operación | ADMIN | CLIENTE | REPARTIDOR |
|---|---|---|---|
| Crear envío | Sí | Solo customerId asociado | No |
| Consultar/listar/tracking/historial | Todos | Solo propios | Solo asignados |
| Asignar/reasignar courier | Sí, envío pendiente | No | No |
| EN_RUTA/ENTREGADO | Según máquina de estados | No | Solo asignados y transición válida |
| Cancelar | Solo PENDIENTE | No en contrato inicial | No |
| Administrar clientes/repartidores | Sí | No | No |
| Disponibilidad para asignación | Sí | No | No |
| Estado interno mínimo courier | Sí | No | Solo courierId propio |

Cliente cancela solo si el equipo amplía explícitamente el permiso: el PDF exige creación/consulta, no cancelación para ese rol. Clientes/listados/identificadores no deben permitir fugas entre usuarios; filtrado de consultas antes de paginar. UUID o tracking no reemplaza autorización. 401 para falta de identidad,403 para denegación; decidir404 en recurso ajeno solo si es política consistente y probada.

## Configuración por ambiente
Bootstrap mínimo local: spring.application.name para localizar configuración, perfil activo e import URL por entorno. config-repo ofrece application-<profile> y/o <service>-<profile> según precedencia documentada; dev y prod para tres clientes. Config Server debe externalizar los valores requeridos sin producir una dependencia circular para su propio arranque.

Externalizar puerto, nombre, datasource, URLs, logging, issuer/audiences/TTL no sensibles. Variables iniciales: CONFIG_SERVER_URL, SPRING_PROFILES_ACTIVE, DB_URL, DB_USERNAME, DB_PASSWORD, COURIER_SERVICE_URL, JWT_PUBLIC_KEY_LOCATION y solo en emisor JWT_PRIVATE_KEY_LOCATION. DB_ROOT_PASSWORD reservado a MySQL; no pasarlo a las apps. Nombres exactos validados en S2-01/S2-25. Secretos obligatorios en entorno/montajes; no valores reales ni defaults inseguros en Git.

Dentro de Compose: http://courier-service:8082 y http://config-server:8888; MySQL por nombre DNS propio. localhost solo para solicitudes desde el host o tests donde realmente es el mismo proceso/host, jamás entre contenedores distintos.

## Pruebas paralelas y cierre de integración
HTTP courier: fixtures200/404/401/403/422/503 y timeout. JWT: ADMIN, CLIENTE A/B, REPARTIDOR A/B firmados localmente; vencido, manipulado,iss/aud incorrectos. Config: JSON de Environment por application/profile servido por mock. DB: MySQL propio por suite. No añadir una dependencia Maven sobre otro microservicio.

Cada PR pasa sus pruebas con dobles; integración final usa los cuatro servicios reales, tres bases y Git config. Docker Hub: cuatro imágenes con tags explícitos y digests; namespace lo proporciona el equipo. Compose de entrega image-only, sin build local, docker compose pull y up -d desde checkout limpio. No confundir mocks con evidencia distribuida.
