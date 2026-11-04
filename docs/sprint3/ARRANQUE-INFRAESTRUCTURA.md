# Arranque de infraestructura del Sprint 3

## Preparacion

Docker Desktop en modo Linux, Java 25, Python 3 y OpenSSL (incluido con Git para
Windows). Mantener los siete repositorios como carpetas hermanas y sincronizar
develop. Las revisiones compatibles estan en deploy/sprint3-sources.json;
main todavia corresponde a otra etapa de entrega.

En config-server, eureka-server, security-service, courier-service, shipment-service
y api-gateway ejecutar `./mvnw.cmd package` (Linux: `./mvnw package`). Verificar que
las suites terminen correctamente antes de construir las imagenes. Las pruebas
de repositorios shipment necesitan Docker/MySQL; sus datos descartables usan
tmpfs de 512 MiB para reducir I/O. Las bases de Compose siguen siendo persistentes.
Config Server tambien se construye desde la fuente actualizada del equipo.

Desde shipment-service:

```powershell
python integration/sprint3_sources.py --workspace ..
python integration/build_sprint3.py --workspace .. --temporary D:/DockerData/build-sprint3
python integration/prepare_sprint3.py
docker compose --env-file target/sprint3/runtime.env -f deploy/docker-compose.yml -p logitrack-sprint3 up -d --no-build --wait --wait-timeout 900
```

La revision de configuracion se toma del manifiesto. Para un runtime.env existente,
conservar todas las credenciales y cambiar solamente CONFIG_GIT_LABEL al SHA de
config-repo indicado en ese archivo. No generar otras claves para las mismas bases.
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

La segunda orden utiliza las rutas publicas `/api/v1/...` del Gateway. Para
incluir la caida y restauracion de courier y la matriz de roles y pertenencia:

```powershell
python integration/verify_sprint3.py --allow-courier-stop
```

La prueba verifica registro real, login y JWT en Gateway, ADMIN/CLIENTE/REPARTIDOR,
pertenencia A/B, CORS permitido/denegado, Feign, 404 y caida/restauracion de courier
sin modificar el envio ni su historial. Finalmente comprueba entrega y estado
terminal. El reporte solo marca fullSprint3Acceptance=true con todos esos pasos.
Genera datos de demo unicos; no borra recursos ni volumenes. La opcion de parada
detiene solamente courier del proyecto indicado y lo restaura al terminar.

Las rutas son `/api/v1/auth/login`, `/api/v1/shipments` y `/api/v1/couriers`,
todas en el puerto 8066. El discovery locator generico esta desactivado:
usar las rutas publicas declaradas. En Postman, utilizar la misma base del
Gateway para los tres servicios y contemplar el DNI requerido por courier.

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
