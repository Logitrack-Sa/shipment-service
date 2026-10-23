# Backlog del Sprint 2

30 issues; 104 puntos totales. Alex35, Gonzalo34, Damián35. No se asignan horas ni fechas sin acuerdo.

| Código | Repo | Responsable | Puntos | Tarea |
|---|---|---|---:|---|
| S2-01 | shipment-service | @4lexxe | 3 | Contratos REST, JWT y matriz de permisos verificables |
| S2-02 | shipment-service | @4lexxe | 5 | Desacoplar Courier de envíos y migrar sin perder auditoría |
| S2-03 | shipment-service | @4lexxe | 5 | Consultar repartidores por REST y manejar fallos de asignación |
| S2-04 | shipment-service | @4lexxe | 5 | Proteger envíos por JWT, rol y pertenencia del recurso |
| S2-05 | shipment-service | @4lexxe | 3 | Consumir configuración central de shipment-service |
| S2-06 | shipment-service | @4lexxe | 3 | Adaptar Dockerfile de envíos al servicio desacoplado |
| S2-07 | shipment-service | @4lexxe | 3 | Conservar regresión de tarifas, estados, cancelación e historial |
| S2-08 | shipment-service | @4lexxe | 3 | Ampliar CI por repositorio sin acoplar los PR a otros servicios |
| S2-09 | shipment-service | @4lexxe | 3 | Verificar el flujo distribuido usando imágenes publicadas |
| S2-10 | shipment-service | @4lexxe | 2 | Documentar entrega, arquitectura y arranque del Sprint 2 |
| S2-11 | security-service | @GonzaCarrillo | 3 | Inicializar security-service con MySQL propio y migraciones |
| S2-12 | security-service | @GonzaCarrillo | 5 | Registrar usuarios y vincular identidades sin elevar privilegios |
| S2-13 | security-service | @GonzaCarrillo | 5 | Autenticar y emitir JWT firmados con claves seguras |
| S2-14 | security-service | @GonzaCarrillo | 2 | Exponer perfil autenticado y proteger administración de usuarios |
| S2-15 | courier-service | @GonzaCarrillo | 3 | Proteger courier-service por JWT y separar consultas internas |
| S2-16 | security-service | @GonzaCarrillo | 3 | Integrar security-service con Config Server sin externalizar secretos |
| S2-17 | security-service | @GonzaCarrillo | 2 | Contenerizar security-service con Java 25 y secretos por montaje |
| S2-18 | security-service | @GonzaCarrillo | 5 | Probar JWT, roles, pertenencia y aislamiento de identidades |
| S2-19 | shipment-service | @GonzaCarrillo | 3 | Preparar Postman del Sprint 2 con tokens y escenarios por rol |
| S2-20 | security-service | @GonzaCarrillo | 3 | Preparar CI independiente y contrato público de autenticación |
| S2-21 | courier-service | @ChoquePablo | 5 | Extraer repartidores a servicio independiente con MySQL propio |
| S2-22 | courier-service | @ChoquePablo | 3 | Actualizar repartidores y administrar estado operativo |
| S2-23 | courier-service | @ChoquePablo | 3 | Exponer disponibilidad y estado mínimo para integración REST |
| S2-24 | config-server | @ChoquePablo | 3 | Implementar Config Server Git con perfiles dev y prod |
| S2-25 | config-repo | @ChoquePablo | 3 | Organizar configuración externa dev/prod y contrato de variables |
| S2-26 | courier-service | @ChoquePablo | 3 | Consumir configuración central y verificar persistencia courier |
| S2-27 | courier-service | @ChoquePablo | 2 | Contenerizar courier-service y comprobar ejecución independiente |
| S2-28 | shipment-service | @ChoquePablo | 5 | Orquestar cuatro servicios y tres MySQL mediante Compose |
| S2-29 | config-server | @ChoquePablo | 3 | Verificar Config Server y repositorio Git en CI |
| S2-30 | shipment-service | @ChoquePablo | 5 | Publicar cuatro imágenes versionadas en Docker Hub y validar entrega |
