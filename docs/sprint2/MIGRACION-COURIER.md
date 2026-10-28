# Migración de repartidores (#54)

V1 queda intacta. V2 elimina únicamente la FK local y conserva los índices de búsqueda existentes. `shipments.courier_id`, envíos, clientes y auditoría se conservan. La tabla `couriers` queda como copia de transición sin entidad, repositorio ni API dentro de shipment. No se elimina hasta que el responsable de courier-service verifique la importación.

Procedimiento en ventana de mantenimiento, sin escrituras concurrentes:
1. Respaldar la base shipment completa con mysqldump y verificar restauración en una base descartable. Guardar el archivo fuera de Git y con acceso restringido.
2. Desde la copia, exportar couriers con todos sus campos y UUID binario convertido mediante `HEX(id)`; importar en la base propia courier conservando UUID con `UNHEX(id)`, created_at/updated_at, autores y version. Coordinar con Damián el esquema de destino; no generar UUID nuevos.
3. Comparar COUNT(*) y las filas ordenadas por HEX(id), con todos los campos; contar envíos asignados e historial antes/después. Comprobar que todos los UUID asignados existan en destino. Probar repartidor activo/inactivo por REST con JWT.
4. Desplegar shipment con V2. Verificar Flyway, asignación y consulta por UUID, tarifa, tracking, cancelación e historial. Conservar respaldo y tabla courier local durante la validación final.
5. Retirar esa tabla solo mediante futura migración acordada después de la confirmación de courier-service. Este cambio no borra la tabla ni necesita que el servicio remoto esté terminado para ejecutar pruebas.

Rollback: detener escrituras, restaurar la copia completa o volver a la versión anterior después de validar que los courier_id asignados durante la transición existan localmente. No reaplicar V1 ni agregar una FK apuntando a otra base. Si hubo nuevos couriers remotos, reconciliar antes de volver.

El código de repartidores del Sprint 1 sigue disponible en el commit `c016c2c`; Damián puede extraerlo desde Git. Las pruebas de migración usan MySQL aislado y datos previos a V2, sin tocar la base local del usuario.
