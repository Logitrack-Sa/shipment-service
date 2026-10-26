package com.logitrack.shipment.client;

import java.util.UUID;

public interface CourierLookup {
    void requireAvailable(UUID courierId);
    void requireActive(UUID courierId);
}
