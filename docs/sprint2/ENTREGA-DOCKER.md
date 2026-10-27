# Entrega Docker del Sprint 2

Responsable de #64 y #65: @4lexxe. Namespace confirmado: `alexxe576`. Las imágenes se llaman `logitrack-shipment-service`, `logitrack-courier-service`, `logitrack-security-service` y `logitrack-config-server`. Este documento describe el procedimiento; una publicación solo se acredita con los digests y el reporte de la ejecución real.

## Arrancar una entrega publicada

Requisitos: Docker Compose v2, Python 3 y OpenSSL (en Windows puede usarse el incluido con Git). Reservar los puertos host 8081, 8082, 8083 y 8888 y al menos 6 GB disponibles para Docker. Descargar un checkout de shipment-service que contenga este Compose y elegir una versión publicada, por ejemplo `sprint2-20261026.1`.

```shell
python integration/prepare_release.py --namespace alexxe576 --version sprint2-20261026.1 --config-label fca10127f9a949b8f4c748bd96d5bd606bf1af1c
docker compose --env-file target/release/runtime.env -f compose.release.yaml -p logitrack-e2e-local pull
docker compose --env-file target/release/runtime.env -f compose.release.yaml -p logitrack-e2e-local up -d --no-build --wait --wait-timeout 300
python integration/provision_release.py --env-file target/release/runtime.env
python integration/verify_release.py --env-file target/release/runtime.env --project logitrack-e2e-local
```

El script prepara material **descartable de demo**: seis contraseñas DB distintas, credenciales ADMIN y actores A/B, y RSA efímera. Guarda los valores únicamente en `target/release`, excluido de Git. No sobrescribe un entorno existente. Usar otra carpeta para otra demo; no regenerar credenciales sobre volúmenes ya inicializados. Para operación mantenida se deben proveer y custodiar las claves/credenciales propias del entorno.

Config Server lee el Git real de config-repo con un SHA explícito; no se usa un servidor Config simulado. Las cuatro aplicaciones usan DNS de Compose y cada MySQL dispone de red interna y volumen exclusivo. La clave privada solo se monta en security-service; shipment y courier reciben únicamente la pública. Las apps se publican en loopback del host. Los healthchecks locales usan HTTP y las dependencias esperan `service_healthy` con límites de arranque.

La provisión crea recursos y luego usuarios vinculados a través de APIs ADMIN reales. El harness comprueba roles, pertenencia A/B, estados, cancelación y que una caída de courier no deje escrituras parciales. Detiene y restaura solamente courier de este proyecto de pruebas. No elimina volúmenes.

La provisión es preparación de datos descartables y no es una transacción distribuida. Si falla, informa el error y conserva los datos creados; no se borran registros para compensar. No repetirla a ciegas sobre el mismo entorno: revisar lo provisionado o usar otra carpeta y otro nombre de proyecto para una demo nueva. La garantía de no dejar escrituras parciales que prueba el harness corresponde a asignación/transiciones de envíos ante el fallo de courier.

## Publicar una versión

Cada repositorio de aplicación incluye `.github/workflows/publish.yml`. Configurar las variables `DOCKERHUB_USERNAME=alexxe576`, `DOCKERHUB_NAMESPACE=alexxe576` y el secreto `DOCKERHUB_TOKEN` con un token Docker Hub de lectura/escritura limitado y con vencimiento. Nunca usar contraseña de cuenta ni pegar el token en issues, logs o el repositorio.

Workflows equivalentes por servicio: [shipment PR #74](https://github.com/Logitrack-Sa/shipment-service/pull/74), [courier PR #17](https://github.com/Logitrack-Sa/courier-service/pull/17), [security PR #21](https://github.com/Logitrack-Sa/security-service/pull/21) y [Config Server PR #8](https://github.com/Logitrack-Sa/config-server/pull/8). Cada workflow publica exclusivamente su servicio; la entrega exige que los cuatro completen la misma versión.

La publicación se dispara por un tag `sprint2-AAAAMMDD.N` o manualmente desde main/develop. Exige que el commit pertenezca a una rama integrada, ejecuta Maven antes del push y publica únicamente ese tag explícito, sin `latest`. Registra el SHA fuente en etiquetas OCI y el RepoDigest en resumen/artefacto. No se ejecuta desde PR ni forks. Las credenciales deben conservarse en GitHub Secrets; si faltan, el job falla antes de publicar.

Los cuatro repos deben publicar la misma versión. Después, ejecutar `pull` y la verificación completa desde un checkout limpio. Los digests identifican exactamente las imágenes comprobadas. Las pruebas de contrato de Compose no sustituyen esa ejecución real.

## Detener y conservar datos

```shell
docker compose --env-file target/release/runtime.env -f compose.release.yaml -p logitrack-e2e-local stop
```

No ejecutar `down -v` contra entornos con datos que se quieran conservar. Las credenciales y claves generadas permanecen locales; no adjuntarlas al entregar evidencia.
