package com.logitrack.shipment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(
        name = "${logitrack.courier.service-id:courier-service}",
        url = "${logitrack.courier.base-url:}",
        fallbackFactory = CourierFeignFallbackFactory.class
)
public interface CourierFeignClient {

    @GetMapping("/api/v1/couriers/{id}/availability")
    CourierResult getAvailability(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/internal/couriers/{id}/status")
    CourierResult getStatus(@PathVariable("id") UUID id);

    record CourierResult(UUID courierId, String status, Boolean available) { }
}
