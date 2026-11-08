package com.pcom.common.dto.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Event payloads (schema version 1). Additive changes only; breaking changes bump EventType.version. */
public final class Events {
    private Events() {}

    public record OrderLine(UUID productId, String productName, BigDecimal unitPrice, int quantity) {}

    public record OrderCreated(UUID orderId, UUID userId, List<OrderLine> lines, BigDecimal totalAmount, String currency) {}
    public record OrderConfirmed(UUID orderId, UUID userId) {}
    public record OrderCancelled(UUID orderId, UUID userId, String reason) {}

    public record InventoryReserved(UUID orderId, UUID userId, BigDecimal totalAmount, String currency) {}
    public record InventoryReservationFailed(UUID orderId, UUID userId, String reason) {}
    public record InventoryReleased(UUID orderId, String reason) {}
    public record InventoryAdjusted(UUID productId, int delta, int available, String reason) {}

    public record PaymentInitiated(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency) {}
    public record PaymentCompleted(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String currency, String gatewayRef) {}
    public record PaymentFailed(UUID paymentId, UUID orderId, UUID userId, String reason) {}
    public record PaymentRefunded(UUID paymentId, UUID orderId, UUID userId, BigDecimal amount, String reason) {}

    public record InvoiceGenerated(UUID invoiceId, String invoiceNumber, UUID orderId, UUID userId, BigDecimal totalAmount, String currency) {}

    public record NotificationTriggered(UUID notificationId, UUID userId, String channel, String template) {}
}
