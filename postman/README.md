# Postman del Sprint 2

Colección original de Gonzalo revisada para los contratos actuales. Importar LogiTrack_Sprint2.postman_collection.json y LogiTrack_Sprint2_Local.postman_environment.json.

## Preparar y ejecutar

Usar un entorno descartable con los tres servicios reales, Config Server y las claves coordinadas. shipment:8081, courier:8082, security:8083, config:8888. Las bases deben ser propias por servicio. Esta colección requiere CRUD/actualización/disponibilidad de courier y los endpoints de administración de security.

Configurar admin_username y admin_password con una cuenta ADMIN del entorno. Los archivos exportados no contienen contraseñas ni tokens. La provisión de ADMIN del emisor se habilita explícitamente con SECURITY_ADMIN_ENABLED y SECURITY_ADMIN_PASSWORD; no hay contraseña por defecto.

Ejecutar todas las carpetas en orden:
1. Setup: ID único, reinicia tokens/IDs y genera contraseñas efímeras CLIENTE/REPARTIDOR.
2. Auth: registra CLIENTE y autentica ADMIN/CLIENTE.
3. ADMIN: crea clientes/couriers, vincula el CLIENTE, renueva su JWT, crea una cuenta REPARTIDOR asociada por ADMIN y la autentica. Crea envíos propios y ajenos.
4. CLIENTE y REPARTIDOR: consultas por pertenencia y flujo EN_RUTA → ENTREGADO.
5. Negativos: 401/403, acceso ajeno existente, courier inactivo y transiciones/cancelaciones inválidas.

La vinculación se hace con PUT /api/v1/users/{id}/customer-link y POST /api/v1/users; nunca mediante registro público privilegiado. Los JWT se renuevan después de vincular identidades. Asignación de courier usa PUT, según el contrato de shipment. Los listados se filtran por el JWT en el servidor.

El Runner crea datos únicos en cada iteración; no borra recursos ni bases. Conservar tokens solo en el entorno local de Postman y limpiar valores antes de exportar. La colección Sprint 1 no usa las identidades distribuidas ni este aprovisionamiento.

## Verificación automatizada

Con Newman 6.2.1 disponible: NEWMAN_MODULE puede indicar la ruta absoluta del módulo y `node postman/verify-contract.mjs` ejecuta dos iteraciones de las 57 solicitudes contra un fixture HTTP de contratos con estado. Comprueba secuencia, variables, métodos y aserciones de la colección. El fixture no es un servicio productivo y no prueba firma RSA ni la integración distribuida. La criptografía/autorización real se verifica en las suites Java; el flujo de cuatro servicios e imágenes publicadas corresponde a #61.

CI ejecuta ese contrato sin secretos ni permisos de escritura. Para evidencia distribuida ejecutar también la colección con servicios reales o Newman apuntando a sus URLs, usando credenciales locales fuera de Git. No publicar reportes que contengan respuestas con JWT o un entorno exportado con secretos.
