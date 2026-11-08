package com.pcom.order.client;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.product.ProductLookupRequest;
import com.pcom.common.dto.product.ProductSnapshot;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resilience4j stack around product-service: RateLimiter -> Retry -> CircuitBreaker -> Bulkhead
 * (the time limit is the Feign read-timeout from configuration).
 */
@Component
public class ProductGateway {
    private final ProductClient client;

    public ProductGateway(ProductClient client) {
        this.client = client;
    }

    @RateLimiter(name = "productService")
    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "lookupFallback")
    @Bulkhead(name = "productService")
    public List<ProductSnapshot> lookup(Collection<UUID> ids) {
        return client.lookup(new ProductLookupRequest(ids));
    }

    List<ProductSnapshot> lookupFallback(Collection<UUID> ids, Throwable t) {
        throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE",
                "Catalog is temporarily unavailable, please retry", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
