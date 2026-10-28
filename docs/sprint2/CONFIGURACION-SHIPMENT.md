# Arranque de shipment en Sprint 2 (#57)

Boot 3.5.16 y Spring Cloud 2025.0.3 (BOM 2025.0.x compatible). El bootstrap versionado contiene nombre, perfil e import; dev y prod requieren Config Server. Un error al obtener configuración aborta el arranque. No hay fallback silencioso a una base local ni seguridad deshabilitada.

1. Damián agrega a config-repo la plantilla `examples/shipment-service-dev.yaml` y su equivalente prod. Ajusta valores de cada ambiente. No copiar contraseñas ni claves privadas al Git de configuración.
2. Levantar Config Server con ese repositorio y MySQL de shipment. Exportar CONFIG_SERVER_URL y SPRING_PROFILES_ACTIVE=dev o prod.
3. Definir DB_URL, DB_USERNAME, DB_PASSWORD. DB_URL apunta a la base propia; en contenedores usar DNS interno y TLS según la configuración MySQL. Montar la clave pública RSA de security-service y definir JWT_PUBLIC_KEY_LOCATION (ejemplo: file:/run/secrets/jwt-public.pem), JWT_KEY_ID, JWT_ISSUER y JWT_AUDIENCE. COURIER_SERVICE_URL apunta a courier-service, sin localhost entre contenedores.
4. IntelliJ: importar Maven, seleccionar JDK25 y agregar esas variables a la configuración de ShipmentServiceApplication. Ejecutar. Desde terminal: `./mvnw spring-boot:run` o `.\mvnw.cmd spring-boot:run`.
5. Comprobar health y documentación. Solicitar un JWT al servicio de seguridad y usar Authorize en `/docs/explorer.html`, el campo JWT de `/docs/`, o Authorization: Bearer en Postman. Sin JWT las rutas de negocio devuelven 401.

`logitrack.courier.connect-timeout` y `read-timeout` requieren duraciones positivas. URL sin credenciales, query ni fragmento. La clave pública y el kid esperado deben coincidir con el emisor; la clave privada pertenece exclusivamente a security-service. Actualizar clave pública/kid y emisor de forma coordinada para rotación. Un usuario sin vínculo customerId/courierId se deniega, aunque el JWT sea válido.

## Verificación independiente
- `./mvnw verify` necesita Docker para MySQL/Testcontainers. Incluye migración V1→V2, pruebas REST y JWT locales.
- Sin Docker: `./mvnw -Dtest='!**/repository/**' verify`; este subconjunto no demuestra MySQL ni migración. Tests activan perfil test y no dependen del Config Server real. Los dobles HTTP usan sockets locales efímeros.
- ConfigClientIntegrationTest arranca el programa completo con Environment remoto simulado en dev/prod y comprueba que falla cuando Config Server falta.
- CI inicia shipment y MySQL reales en contenedores con `.github/compose.smoke.yaml`. Config y courier son dobles declarados, la firma JWT RSA es real y efímera. No es evidencia de la integración final de cuatro aplicaciones; esa corresponde a #61/#64.

Dockerfile #58 adaptado: multietapa, bases por digest, usuario10001 y contexto mínimo. `compose.yaml` ejecuta shipment y su MySQL en desarrollo, con puerto 8081, validación Flyway/JPA y la clave pública montada. Copiar `.env.example` a `.env`, completar contraseñas, URLs y parámetros JWT y ejecutar `docker compose up -d --build`. Config Server y courier deben estar disponibles desde el contenedor; este archivo no arranca esos servicios ni reemplaza el Compose image-only de #64. No usar credenciales de prueba o el override de CI en producción.

`DB_USERNAME` es la variable de la aplicación y del Compose actualizado. Las bases creadas antes conservan sus credenciales originales: cambiar `.env` no cambia usuarios de un volumen MySQL existente. No borrar el volumen para corregir credenciales; usar una base de prueba nueva o acordar el ajuste con el administrador.

Para IntelliJ sin dependencias externas ver [demo y pruebas locales](INTELLIJ.md). Para la entrega de cuatro servicios, [integración real](INTEGRACION.md).
