package com.logitrack.shipment.entity;

/** Estados de un envío; las transiciones se implementan en la issue #22. */
public enum ShipmentStatus {
    PENDIENTE, EN_RUTA, ENTREGADO, CANCELADO
}
