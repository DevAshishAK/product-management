package com.pcom.order.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID orderId;
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    private String reason;
    private Instant changedAt = Instant.now();

    public OrderStatusHistory(UUID orderId, OrderStatus status, String reason) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
    }
}
