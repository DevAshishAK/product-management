package com.pcom.order.web;

import com.pcom.order.entity.OrderEntity;
import com.pcom.order.entity.OrderItem;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.entity.OrderStatusHistory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}

    public record PlaceOrderRequest(@NotEmpty @Size(max = 100) List<@Valid Line> items) {
        public record Line(@NotNull UUID productId, @Min(1) @Max(1000) int quantity) {}
    }

    public record CancelRequest(@Size(max = 500) String reason) {}

    public record OrderItemResponse(UUID productId, String productName, BigDecimal unitPrice, int quantity) {
        static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity());
        }
    }

    public record OrderResponse(UUID id, UUID userId, OrderStatus status, BigDecimal totalAmount, String currency,
                                String cancelReason, Instant createdAt, Instant updatedAt, List<OrderItemResponse> items) {
        public static OrderResponse from(OrderEntity o) {
            return new OrderResponse(o.getId(), o.getUserId(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                    o.getCancelReason(), o.getCreatedAt(), o.getUpdatedAt(),
                    o.getItems().stream().map(OrderItemResponse::from).toList());
        }
    }

    public record StatusEntry(OrderStatus status, String reason, Instant changedAt) {
        static StatusEntry from(OrderStatusHistory h) {
            return new StatusEntry(h.getStatus(), h.getReason(), h.getChangedAt());
        }
    }

    public record OrderStatusResponse(UUID orderId, OrderStatus status, List<StatusEntry> history) {
        public static OrderStatusResponse of(OrderEntity o, List<OrderStatusHistory> history) {
            return new OrderStatusResponse(o.getId(), o.getStatus(), history.stream().map(StatusEntry::from).toList());
        }
    }
}
