# Sprint 3: trabajo de Alex

## Alcance y dependencias

Tablero: https://github.com/orgs/Logitrack-Sa/projects/3.
Alex es responsable de #77 (Eureka), #78 (registro de clientes), #79
(configuracion de descubrimiento) y responsable principal de #87 (Compose).
En #88 y #89 aporta infraestructura y evidencia, manteniendo los responsables
principales de pruebas integrales y documentacion.

Se reutilizan las ramas de Pablo para #84-#86 y la base del Gateway de Gonzalo
para #80. Los PR de registro de shipment y Gateway se apilan sobre esas ramas;
no cambian autoria ni implementan nuevamente Feign, rutas, JWT o CORS.
La promocion a main requiere integrar primero esas dependencias.

## Secuencia de commits prevista

| Orden | Repositorio | Cambio verificable |
|---|---|---|
| 1 | eureka-server | Inicializar el repositorio y su alcance |
| 2 | eureka-server | Implementar Eureka y probar registro/heartbeat/baja |
| 3 | eureka-server | Preparar Docker, CI y arranque con Config Server |
| 4 | config-repo | Centralizar Eureka y el perfil sprint3; conservar Sprint 2 |
| 5 | security-service | Registrar seguridad conservando login/JWT |
| 6 | api-gateway | Alinear descubrimiento y bootstrap obligatorio |
| 7 | courier-service | Registrar courier y recuperar fixtures del contrato DNI |
| 8 | shipment-service | Integrar descubrimiento sobre la rama Feign de Pablo |
| 9 | shipment-service | Preparar Compose de nueve contenedores y entorno local |
| 10 | shipment-service | Verificar infraestructura y documentar evidencia/limitaciones |

Los mensajes describen cambios reales, en espanol. No se alteran fechas ni
autores. Las correcciones detectadas por CI pueden requerir commits adicionales:
el checkout de courier contenia un gitlink sin .gitmodules y el Javadoc del
Gateway tenia encabezados de nivel incorrecto. La prueba distribuida tambien
detecto la falta de la configuracion base de Gateway para su bootstrap
obligatorio; se corrige con un commit independiente en config-repo. La revision
IA identifico ademas defaults localhost en descubrimiento; se exigen las
variables ya declaradas en Compose y se actualiza la revision documentada.
El resultado son 15 commits, incluyendo la inicializacion del repositorio Eureka.

## Decisiones

- Mantener Java 25, Boot 3.5.16 y Cloud 2025.0.3, como la base de LogiTrack.
  Los ejemplos docentes mezclan versiones; son referencia conceptual y no una
  orden de migrar todo el stack a Boot 4.
- `config-repo/*-sprint3.properties` activa Eureka sin cambiar el arranque del
  Sprint 2. Los clientes usan `prod,sprint3`; Eureka usa `prod`.
- `compose.release.yaml` conserva la entrega publicada del Sprint 2. La
  infraestructura del Sprint 3 esta en `deploy/docker-compose.yml`, ruta de #87.
- Las tres bases conservan volumenes separados; el Gateway es el unico puerto
  de negocio publicado. Config Server y Eureka exponen administracion solo en
  loopback para la demostracion local.
- `logitrack.courier.base-url` queda vacia en sprint3 para resolver por Eureka;
  el DNS publicado por cada cliente usa `SERVICE_HOST`. Este perfil exige
  `SERVICE_HOST` y `EUREKA_SERVER_URL`, sin fallback silencioso a localhost.
- No se publican credenciales, PEM privados, tokens ni runtime.env.
- Las imagenes `sprint3-local` son compilaciones locales; no se presentan como
  imagenes publicadas en Docker Hub ni sobrescriben el tag del Sprint 2.

## Material de referencia

La consigna Sprint 3 define requisitos de LogiTrack. El apunte Parte II y los
archivos eureka-gateway/hotels-rooms aportan ejemplos de registro, discovery,
Feign y Compose. La coleccion docente usa hoteles y habitaciones; sus rutas y
credenciales no corresponden a LogiTrack.

La limpieza local se limito a cache de compilacion antigua. El traslado de
Docker a HDD conserva todos los contenedores y volumenes anteriores. El HDD
libera espacio del SSD, pero puede necesitar tiempos de inicializacion mayores.
