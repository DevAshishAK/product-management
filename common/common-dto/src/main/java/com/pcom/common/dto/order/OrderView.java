package com.pcom.common.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderView(UUID id, UUID userId, String status, BigDecimal totalAmount, String currency,
                        Instant createdAt, List<OrderLineView> lines) {
    public record OrderLineView(UUID productId, String productName, BigDecimal unitPrice, int quantity) {}
}
