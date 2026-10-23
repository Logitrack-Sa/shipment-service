# Probar desde IntelliJ IDEA

Importar pom.xml con JDK25 y recargar Maven. El selector Run reconoce las configuraciones compartidas de .run/:

- **Sprint2_Pruebas**: Maven verify con `-Dtest=!**/repository/**`. Ejecuta unitarias, regresión con H2, JWT real y Config/HTTP simulados. No necesita Docker ni el emisor de compañeros. El reporte Maven/JaCoCo queda en target/.
- **Sprint2_Demo**: spring-boot:run con la clase de test LocalShipmentApplication y test classpath. Arranca shipment en http://localhost:8081/docs/ con H2 volátil y dos dobles HTTP locales. No usa la base del Sprint 1. Al detenerlo se pierden los datos. La clase y H2 no entran en la imagen productiva.

La demo crea cinco identidades y JWT RS256 efímeros (10 minutos) en target/local-demo/session.json; no imprime tokens. Para probar desde el portal o Swagger, copiar un token de esa sesión. Reiniciar la demo para renovar la sesión. No subir ese archivo a Git.

Con Sprint2_Demo ejecutándose, desde otra consola:

```shell
python dev/smoke_demo.py
```

Comprueba creación/asignación, firma JWT, permisos y aislamiento A/B, listados, tracking, entrega, cancelación y auditoría. Escribe target/local-demo/result.json con modo simulated-local. Este smoke no sustituye MySQL ni las cuatro aplicaciones reales.

Para todas las pruebas MySQL y migración: Docker activo y `./mvnw verify`. La CI ejecuta ese conjunto y arranca la imagen con MySQL real, Config/Courier simulados y usuario10001. El perfil real dev/prod usa las variables de [configuración](CONFIGURACION-SHIPMENT.md), no la demo.

La configuración anterior ShipmentService Local se conserva para la historia del Sprint 1; no seleccionarla para el arranque distribuido sin adaptar su entorno. [Documentación histórica](../sprint1/README.md).
