package com.pcom.order.entity;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    private UUID productId;
    private String productName;
    private BigDecimal unitPrice;
    private int quantity;
}
