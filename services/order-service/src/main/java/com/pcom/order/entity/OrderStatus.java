package com.pcom.order.entity;

import java.util.Set;

public enum OrderStatus {
    PENDING, INVENTORY_RESERVED, CONFIRMED, CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return allowed().contains(next);
    }

    private Set<OrderStatus> allowed() {
        return switch (this) {
            case PENDING -> Set.of(INVENTORY_RESERVED, CONFIRMED, CANCELLED);
            case INVENTORY_RESERVED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> Set.of(CANCELLED);
            case CANCELLED -> Set.of();
        };
    }
}
