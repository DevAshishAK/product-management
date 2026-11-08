package com.pcom.invoice.client;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.order.OrderView;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderGateway {
    private final OrderClient client;

    public OrderGateway(OrderClient client) {
        this.client = client;
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "fallback")
    @Bulkhead(name = "orderService")
    public OrderView get(UUID orderId) {
        return client.get(orderId);
    }

    OrderView fallback(UUID orderId, Throwable t) {
        // Thrown inside a Kafka listener: the message goes through retry topics until the order service recovers.
        throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "Order service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
