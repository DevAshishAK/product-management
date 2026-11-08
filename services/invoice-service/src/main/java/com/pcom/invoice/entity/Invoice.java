package com.pcom.invoice.entity;

import com.pcom.common.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invoice")
public class Invoice extends AuditableEntity {
    private String invoiceNumber;
    private UUID orderId;
    private UUID userId;
    private BigDecimal totalAmount;
    private String currency;
    private String status = "ISSUED";
    private Instant issuedAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "invoice_line", joinColumns = @JoinColumn(name = "invoice_id"))
    private List<InvoiceLine> lines = new ArrayList<>();
}
