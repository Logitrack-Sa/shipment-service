package com.logitrack.shipment.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de un envío y su máquina de transiciones permitidas.
 *
 * <p>El flujo soportado es {@link #PENDIENTE} → {@link #EN_RUTA} → {@link #ENTREGADO} y
 * {@link #PENDIENTE} → {@link #CANCELADO}. {@link #ENTREGADO} y {@link #CANCELADO} son
 * estados terminales: no admiten transiciones hacia ningún otro estado.</p>
 *
 * <table border="1">
 *   <caption>Transiciones permitidas</caption>
 *   <tr><th>Desde \ Hacia</th><th>PENDIENTE</th><th>EN_RUTA</th><th>ENTREGADO</th><th>CANCELADO</th></tr>
 *   <tr><th>PENDIENTE</th><td>No</td><td>Si</td><td>No</td><td>Si</td></tr>
 *   <tr><th>EN_RUTA</th><td>No</td><td>No</td><td>Si</td><td>No</td></tr>
 *   <tr><th>ENTREGADO</th><td>No</td><td>No</td><td>No</td><td>No</td></tr>
 *   <tr><th>CANCELADO</th><td>No</td><td>No</td><td>No</td><td>No</td></tr>
 * </table>
 *
 * <p>La regla de negocio vive en este enum, no en los controladores ni en los servicios.</p>
 */
public enum ShipmentStatus {
    /** Envío registrado y aún no despachado; permite pasar a {@link #EN_RUTA} o {@link #CANCELADO}. */
    PENDIENTE,
    /** Envío despachado y en camino; solo permite pasar a {@link #ENTREGADO}. */
    EN_RUTA,
    /** Envío entregado al cliente; estado terminal. */
    ENTREGADO,
    /** Envío cancelado; estado terminal. */
    CANCELADO;

    /**
     * Mapa inmutable con las transiciones válidas desde cada estado. Un estado terminal
     * se mapea a un conjunto vacío.
     */
    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = transitions();

    /**
     * Indica si este estado puede transicionar hacia {@code target}.
     *
     * @param target estado destino; {@code null} siempre devuelve {@code false}
     * @return {@code true} si la transición está permitida, {@code false} en caso contrario
     */
    public boolean canTransitionTo(ShipmentStatus target) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * Construye el mapa de transiciones permitidas de la máquina de estados.
     *
     * @return mapa inmutable de estado de origen a estados de destino permitidos
     */
    private static Map<ShipmentStatus, Set<ShipmentStatus>> transitions() {
        Map<ShipmentStatus, Set<ShipmentStatus>> map = new EnumMap<>(ShipmentStatus.class);
        map.put(PENDIENTE, allowed(EN_RUTA, CANCELADO));
        map.put(EN_RUTA, allowed(ENTREGADO));
        map.put(ENTREGADO, allowed());
        map.put(CANCELADO, allowed());
        return Collections.unmodifiableMap(map);
    }

    /**
     * Crea un conjunto inmutable con los estados destino indicados.
     *
     * @param targets estados destino permitidos; puede estar vacío para estados terminales
     * @return conjunto inmutable con los destinos indicados
     */
    private static Set<ShipmentStatus> allowed(ShipmentStatus... targets) {
        EnumSet<ShipmentStatus> set = EnumSet.noneOf(ShipmentStatus.class);
        Collections.addAll(set, targets);
        return Collections.unmodifiableSet(set);
    }
}
