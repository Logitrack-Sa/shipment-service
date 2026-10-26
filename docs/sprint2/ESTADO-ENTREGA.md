# Estado comprobado al 26/10/2026

La entrega de envíos se reúne en el [PR #69](https://github.com/Logitrack-Sa/shipment-service/pull/69), que incluye el PR #70 ya fusionado en su rama. No alcanza con fusionar a una rama intermedia: la disponibilidad para el equipo se comprueba en main y en los checks del commit integrado.

## Shipment

| Issues | Entrega |
|---|---|
| #53–#57 | Contratos, extracción de Courier, cliente REST, JWT por rol/pertenencia y Config Client dev/prod |
| #58–#59 | Imagen Java 25 sin privilegios, contexto mínimo, regresión y migración V1 → V2 |
| #60 | CI de shipment y PRs de CI independiente en los otros cuatro repositorios; no confundir PR abierto con integración en main |
| #61 | Ejecutor de integración implementado; ejecución real pendiente del Compose y las cuatro imágenes publicadas |
| #62 | Arquitectura, arranque y evidencia documentados; faltan enlaces/digests de la publicación #65 |

`compose.yaml` es desarrollo de shipment/MySQL con build local, variables requeridas, clave pública montada y puerto 8081. El override de CI agrega Config/Courier simulados. Ninguno es la entrega image-only de cuatro servicios.

## Dependencias comprobadas

- Security: [PR #12](https://github.com/Logitrack-Sa/security-service/pull/12) contiene usuarios, roles, Flyway y MySQL propio; no implementa los endpoints de login/registro ni emisión JWT de las siguientes issues. No hay ramas remotas publicadas con esos endpoints en la revisión de esta fecha.
- Courier: Damián publicó ramas de CRUD, disponibilidad, Config Client, Docker y seguridad. Los PR siguen abiertos y necesitan reunir esas implementaciones; shipment ya usa los endpoints de disponibilidad/estado acordados, sin acceso a su base.
- Config Server: [PR #7](https://github.com/Logitrack-Sa/config-server/pull/7) corrige la base del PR #5. Suite y smoke Docker aprobados con Git local, todavía sin fusionar.
- Config repo: la entrega inicial está en main. [PR #5](https://github.com/Logitrack-Sa/config-repo/pull/5) alinea propiedades con el consumidor shipment y corrige perfiles prod; pendiente de fusión.
- Publicación: no hay releases ni tags/digests de cuatro imágenes verificados. La configuración consultada de shipment no incluye credenciales Docker Hub comprobadas. #64 y #65 siguen abiertos. No se inventan namespace, tags, digests o cuentas de prueba.

El workflow de integración valida la entrega de main y registra el SHA real del checkout junto con los digests descargados. No certifica un PR diferente ni presenta los dobles como servicios reales. El estado blocked significa que no se ejecutó la validación distribuida; no es un fallo de las pruebas de shipment.
