package com.pcom.payment.web;

import com.pcom.payment.entity.Payment;
import com.pcom.payment.entity.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record InitiatePaymentRequest(@NotNull UUID orderId, @NotNull UUID userId,
                                         @NotNull @DecimalMin("0.01") BigDecimal amount,
                                         @NotBlank @Size(min = 3, max = 3) String currency) {}

    /** amount null = refund everything that is still refundable. */
    public record RefundRequest(@DecimalMin("0.01") BigDecimal amount, @Size(max = 500) String reason) {}

    public record PaymentResponse(UUID id, UUID orderId, UUID userId, BigDecimal amount, String currency,
                                  PaymentStatus status, String gatewayRef, String failureReason,
                                  BigDecimal refundedAmount, Instant createdAt) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getUserId(), p.getAmount(), p.getCurrency(),
                    p.getStatus(), p.getGatewayRef(), p.getFailureReason(), p.getRefundedAmount(), p.getCreatedAt());
        }
    }
}
