package com.pcom.invoice.web;

import com.pcom.invoice.entity.Invoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {
    private InvoiceDtos() {}

    public record LineResponse(String description, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}

    public record InvoiceResponse(UUID id, String invoiceNumber, UUID orderId, UUID userId, BigDecimal totalAmount,
                                  String currency, String status, Instant issuedAt, List<LineResponse> lines) {
        public static InvoiceResponse from(Invoice i) {
            return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getOrderId(), i.getUserId(), i.getTotalAmount(),
                    i.getCurrency(), i.getStatus(), i.getIssuedAt(),
                    i.getLines().stream().map(l -> new LineResponse(l.getDescription(), l.getUnitPrice(), l.getQuantity(), l.getLineTotal())).toList());
        }
    }
}
