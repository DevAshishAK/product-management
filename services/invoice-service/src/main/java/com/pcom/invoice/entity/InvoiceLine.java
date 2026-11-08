package com.pcom.invoice.entity;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceLine {
    private String description;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;
}
