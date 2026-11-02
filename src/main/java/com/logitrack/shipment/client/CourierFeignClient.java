package com.logitrack.shipment.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "${logitrack.courier.service-id:courier-service}",
        url = "${logitrack.courier.base-url:}",
        fallbackFactory = CourierFeignFallbackFactory.class
)
public interface CourierFeignClient {

    @GetMapping("/api/v1/couriers/{id}/availability")
    CourierResult getAvailability(@PathVariable("id") UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetMapping("/api/v1/internal/couriers/{id}/status")
    CourierResult getStatus(@PathVariable("id") UUID id,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    record CourierResult(UUID courierId, String status, Boolean available) { }
}
