package com.pcom.invoice.client;

import com.pcom.common.dto.order.OrderView;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service")
public interface OrderClient {
    @GetMapping("/internal/orders/{id}")
    OrderView get(@PathVariable("id") UUID id);
}
