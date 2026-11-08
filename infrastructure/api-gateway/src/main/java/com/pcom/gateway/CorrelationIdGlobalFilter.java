package com.pcom.gateway;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Ensures every request entering the platform has a correlation id and request id. */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {
    static final String CORRELATION_ID = "X-Correlation-Id";
    static final String REQUEST_ID = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID);
        String correlationId = (incoming == null || incoming.isBlank() || incoming.length() > 128)
                ? UUID.randomUUID().toString() : incoming;
        String requestId = UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(CORRELATION_ID, correlationId).header(REQUEST_ID, requestId).build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID, correlationId);
        exchange.getResponse().getHeaders().set(REQUEST_ID, requestId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
