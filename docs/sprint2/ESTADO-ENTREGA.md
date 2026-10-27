# Estado comprobado al 26/10/2026

La entrega Docker `sprint2-20261026.1` está publicada y probada. [Release con los cuatro digests y el reporte](https://github.com/Logitrack-Sa/shipment-service/releases/tag/sprint2-20261026.1); [ejecución distribuida real desde checkout limpio](https://github.com/Logitrack-Sa/shipment-service/actions/runs/40436111809). Pasaron los cinco escenarios con cuatro aplicaciones y tres MySQL, JWT, pertenencia A/B y caída/restauración de courier sin escrituras parciales.

## Integración y alcance

- Shipment: dominio y harness integrados previamente en main mediante PR #69. Compose, publicación y provisión se integraron en develop mediante [PR #74](https://github.com/Logitrack-Sa/shipment-service/pull/74), commit `f048ce419b7a431510b05a05b135ae3c95e58454`.
- Courier: funcionalidades, seguridad y Config Client reunidos en develop mediante [PR #17](https://github.com/Logitrack-Sa/courier-service/pull/17); publicación corregida en [PR #18](https://github.com/Logitrack-Sa/courier-service/pull/18). CI Maven/MySQL/Docker aprobada.
- Security: login, JWT, roles y usuarios disponibles en develop; publicación y espera acotada del bootstrap ADMIN mediante [PR #21](https://github.com/Logitrack-Sa/security-service/pull/21) y [PR #22](https://github.com/Logitrack-Sa/security-service/pull/22). CI Java/MySQL y smoke Docker aprobados.
- Config Server: base corregida integrada en develop mediante [PR #8](https://github.com/Logitrack-Sa/config-server/pull/8) y publicación ajustada en [PR #9](https://github.com/Logitrack-Sa/config-server/pull/9). Ocho pruebas y smoke con Git aprobados.
- Config repo: se usa exactamente `fca10127f9a949b8f4c748bd96d5bd606bf1af1c`, disponible en la rama correctiva del [PR #5](https://github.com/Logitrack-Sa/config-repo/pull/5). Ese PR sigue pendiente; no afirmar que su main ya tenga esa configuración.

Los tags de los cuatro servicios corresponden a los commits del manifiesto. La release es preliminar porque sigue pendiente la revisión humana y la promoción de estas ramas a main. Los checks y el reporte no equivalen a esa aprobación.

## Issues y evidencia

| Issues | Resultado técnico |
|---|---|
| #53–#60 | Funcionalidad shipment, contratos, seguridad, migraciones y CI previamente integrados; consultar sus checks e historial |
| #61 | Los cinco escenarios del harness pasaron ahora con las cuatro imágenes publicadas; no son dobles HTTP |
| #62 | Enlaces de imágenes, versiones, digests y pasos de arranque completados en esta documentación |
| #64 | Compose image-only verificado: siete contenedores saludables, redes/volúmenes/credenciales aislados y claves montadas |
| #65 | Cuatro publicaciones de Actions exitosas en alexxe576; tags inmutables y revisiones OCI contrastadas con Git |

[Guía de entrega y requisitos](ENTREGA-DOCKER.md). `compose.yaml` y el override smoke conservan sus alcances de desarrollo y CI con dobles; la entrega distribuida es `compose.release.yaml`. Newman contra APIs reales ejecutó 114 solicitudes y 200 aserciones, sin fallos.

Las credenciales de demo se generan localmente en target/release, fuera de Git; no se publican claves ni runtime.env. No regenerar credenciales sobre volúmenes existentes ni borrar volúmenes para repetir pruebas.
