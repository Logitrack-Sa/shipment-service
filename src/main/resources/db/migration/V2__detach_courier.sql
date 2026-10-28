-- Conservar UUID y couriers como copia de transición. No borrar datos del Sprint 1.
ALTER TABLE shipments DROP FOREIGN KEY fk_shipments_courier;
