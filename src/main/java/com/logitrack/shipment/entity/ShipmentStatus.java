package com.logitrack.shipment.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Estados de un envío y su máquina de transiciones permitidas. */
public enum ShipmentStatus {
    PENDIENTE, EN_RUTA, ENTREGADO, CANCELADO;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = transitions();

    public boolean canTransitionTo(ShipmentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    private static Map<ShipmentStatus, Set<ShipmentStatus>> transitions() {
        Map<ShipmentStatus, Set<ShipmentStatus>> map = new EnumMap<>(ShipmentStatus.class);
        map.put(PENDIENTE, allowed(EN_RUTA, CANCELADO));
        map.put(EN_RUTA, allowed(ENTREGADO));
        map.put(ENTREGADO, allowed());
        map.put(CANCELADO, allowed());
        return Collections.unmodifiableMap(map);
    }

    private static Set<ShipmentStatus> allowed(ShipmentStatus... targets) {
        EnumSet<ShipmentStatus> set = EnumSet.noneOf(ShipmentStatus.class);
        Collections.addAll(set, targets);
        return Collections.unmodifiableSet(set);
    }
}
