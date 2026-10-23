# Integración real y evidencia (#61)

Estado: harness implementado; **ejecución final pendiente de #64/#65 y de los servicios de compañeros**. No hay namespace ni tags publicados verificados. No se sustituyen imágenes reales por dobles para cerrar esta tarea.

## Preparar el entorno descartable

1. Incorporar el Compose image-only de Damián (por ejemplo compose.release.yaml) al checkout. Debe contener shipment-service, courier-service, security-service, config-server y tres MySQL, sin build. Usar tags explícitos o digests de registro; latest y etiquetas únicamente locales no sirven como evidencia.
2. Configurar las variables/montajes que documente ese Compose: credenciales separadas de MySQL, repositorio Git de Config, par RSA del emisor y clave pública en consumidores. El harness no inventa claves productivas ni cuentas. El Compose debe iniciar independientemente y tener healthchecks.
3. En ese entorno de prueba, provisionar por ADMIN cinco usuarios: ADMIN, CLIENTE A/B y REPARTIDOR A/B. Vincular cada cliente/repartidor a su recurso de dominio, con IDs distintos entre A y B. Dejar ambos couriers ACTIVO. Confirmar el contrato real de login y /me. Los endpoints de provisión pertenecen a security-service; el harness no supone un registro público privilegiado ni permite elegir IDs ajenos.
4. Exportar las variables nombradas en [integration.json](examples/integration.json): cuatro URLs, cinco nombres de usuario y cinco contraseñas de test. loginField admite email o username según la API entregada. No guardar valores secretos en ese archivo ni en Git.

## Ejecutar

Desde la raíz, con Docker, Python3 y un checkout limpio de la entrega:

```shell
docker compose -f compose.release.yaml -p logitrack-e2e-local pull
docker compose -f compose.release.yaml -p logitrack-e2e-local up -d --no-build --wait
python integration/run.py --manifest docs/sprint2/examples/integration.json --compose-file compose.release.yaml --project logitrack-e2e-local --allow-courier-stop
```

El flag autoriza detener **solo courier-service de ese proyecto descartable** durante la prueba y volver a levantarlo en finally. No se eliminan volúmenes. No ejecutarlo contra producción. Se exige prefijo logitrack-e2e- y el Compose debe estar dentro del checkout.

Escenarios: health de cuatro apps y configuración externa; login y /me; crear cliente/courier; crear envío, asignar, pasar EN_RUTA/ENTREGADO; tracking, listados e historial; JWT inválido, acceso de cliente/repartidor ajeno y suplantación de customerId; cancelación y estados terminales; courier detenido sin modificar envío ni historial.

Las cuentas A/B ya vinculadas son fixtures del entorno; los recursos adicionales creados por la prueba no crean cuentas automáticamente. Los tests crean datos únicamente en ese entorno de integración.

## Evidencia y límites

`target/integration/report.json` registra passed/failed/blocked, etapas, referencias de imágenes y RepoDigests reales, hora y líneas relevantes de arranque/migración/error. No exporta contraseñas ni JWT. La salida 2 identifica prerrequisitos pendientes; 1 identifica fallos de aserción/contrato; 0 significa flujo completo aprobado. Un reporte blocked o los tests del harness no prueban el sistema distribuido.

La suite `python -m unittest discover -s integration -p test_harness.py` comprueba el harness con dobles, incluyendo fuga en listados, errores HTTP y rechazo de build/latest/imagen sin digest. No se etiqueta como evidencia distribuida.

`.github/workflows/integration.yml` es manual, checkout de main, token read-only y environment sprint2-integration. Configurar allí variables E2E_*_USERNAME y secretos E2E_*_PASSWORD; las variables/montajes propios del Compose se completan al integrar #64/#65. Si falta Compose o cuentas, falla antes de arrancar. No bloquea los PR individuales y no descarga main de otros repositorios.

Los enlaces Docker Hub por versión y los digests se completarán con la salida real de #65. Hasta entonces no se declara publicación ni integración aprobada.
