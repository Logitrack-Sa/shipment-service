# Arranque de infraestructura del Sprint 3

## Preparacion

Docker Desktop en modo Linux, Java 25, Python 3 y OpenSSL (incluido con Git para
Windows). Mantener los siete repositorios como carpetas hermanas. Usar las ramas
de los PR indicados en PLAN-Y-ALCANCE.md hasta su integracion; main por si solo
todavia no contiene todos los cambios del equipo.

En eureka-server, security-service, courier-service, shipment-service y
api-gateway ejecutar `./mvnw.cmd package` (Linux: `./mvnw package`). Verificar que
las suites terminen correctamente antes de construir las imagenes. Las pruebas
de repositorios shipment necesitan Docker/MySQL; sus datos descartables usan
tmpfs de 512 MiB para reducir I/O. Las bases de Compose siguen siendo persistentes.
Config Server reutiliza la imagen fijada de la entrega Sprint 2.

Desde shipment-service:

```powershell
python integration/build_sprint3.py --workspace .. --temporary D:/DockerData/build-sprint3
python integration/prepare_sprint3.py --config-label e996cd5f3374f38d0a1e3e19de152e8f5cedc29d
docker compose --env-file target/sprint3/runtime.env -f deploy/docker-compose.yml -p logitrack-sprint3 up -d --no-build --wait --wait-timeout 900
```

El SHA de ejemplo debe coincidir con el commit publicado del PR de config-repo;
comprobarlo con `git -C ../config-repo rev-parse HEAD` y usar ese valor completo.
La carpeta temporal puede cambiarse a otro disco con espacio disponible.
Las imagenes se construyen desde JAR actuales, usando el mismo runtime Java no
root y sin volver a descargar Maven dentro de cada build.

El script no sobrescribe un entorno existente. Conservar target/sprint3 y sus
claves junto con los volumenes. Para otra instalacion, usar otra carpeta de
entorno y otro nombre de proyecto; no reutilizar contrasenas nuevas con bases
ya inicializadas.

## Componentes

| Acceso desde el host | Uso |
|---|---|
| http://localhost:8066 | Gateway y peticiones de negocio (8080 dentro del contenedor) |
| http://localhost:8065/ | Dashboard administrativo de Eureka |
| http://localhost:8888/actuator/health | Salud de Config Server |

Los puertos 8081/8082/8083 pertenecen a la red interna. No iniciar esas mismas
aplicaciones desde IntelliJ en paralelo con esta demostracion.

Orden de disponibilidad: Config Server, Eureka, servicios con sus propias
bases y finalmente Gateway. Los healthchecks comprueban HTTP y SQL reales;
Config Server debe servir configuracion Git de Eureka antes de declararse listo,
evitando que los clientes compitan con la primera clonacion en un HDD frio;
la verificacion del catalogo espera ademas los cuatro clientes UP en Eureka.

## Verificar

```powershell
python -m unittest discover -s integration -p test_sprint3_compose.py -v
python integration/verify_sprint3.py
```

La segunda orden exige las rutas publicas definitivas `/api/v1/...` del Gateway.
Mientras #81-#83 esten pendientes, el modo de diagnostico explicito usa las
rutas de discovery de la base #80:

```powershell
python integration/verify_sprint3.py --discovery-routes --allow-courier-stop
```

Ese modo prueba registro real, login a traves de Gateway, acceso con/sin Bearer,
asignacion mediante Feign y Eureka, 404 y caida/restauracion de courier sin
modificar el envio. El reporte distingue este smoke de la aceptacion integral:
no acredita JWT/CORS centralizados ni toda la matriz de roles de #88.
Genera datos de demo unicos; no borra recursos ni volumenes. La opcion de parada
detiene solamente courier del proyecto indicado y lo restaura al terminar.

Las rutas temporales de diagnostico son:
`/security-service/api/v1/auth/login`, `/shipment-service/api/v1/shipments` y
`/courier-service/api/v1/couriers`, todas en el puerto 8066. La coleccion docente
de hoteles no utiliza estos contratos. La coleccion Postman de LogiTrack requiere
adaptar su base al Gateway y contemplar el DNI del contrato actual de courier.

Si 8066 esta ocupado, preparar el entorno con `--gateway-port 18080` o ajustar
solo GATEWAY_PORT en su runtime.env existente. El puerto interno permanece 8080
y el verificador lee el puerto publicado desde ese archivo.

## Detener conservando datos

```powershell
docker compose --env-file target/sprint3/runtime.env -f deploy/docker-compose.yml -p logitrack-sprint3 stop
```

Para continuar usar de nuevo `up -d --no-build --wait --wait-timeout 900` con el
mismo env-file, Compose y nombre de proyecto. Los volumenes del Sprint 2 no se
tocan; el nuevo proyecto crea su propio conjunto de bases.
