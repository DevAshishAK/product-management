package com.pcom.payment.gateway;

import java.math.BigDecimal;

/** Port to the payment service provider (Stripe, Adyen, Razorpay ...). */
public interface PaymentGateway {
    record ChargeResult(boolean success, String reference, String failureReason) {}
    record RefundResult(boolean success, String reference) {}

    /** idempotencyKey must be forwarded to the PSP so retried calls never double-charge. */
    ChargeResult charge(String idempotencyKey, BigDecimal amount, String currency);

    RefundResult refund(String gatewayReference, BigDecimal amount);
}
