package com.pcom.payment.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Demo PSP: approves everything up to app.payment.max-amount. Replace with a real adapter. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {
    private final BigDecimal maxAmount;

    public SimulatedPaymentGateway(@Value("${app.payment.max-amount:100000}") BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    @Override
    @CircuitBreaker(name = "paymentGateway")
    @Retry(name = "paymentGateway")
    public ChargeResult charge(String idempotencyKey, BigDecimal amount, String currency) {
        if (amount.compareTo(maxAmount) > 0) {
            return new ChargeResult(false, null, "Amount exceeds the card limit");
        }
        return new ChargeResult(true, "sim_" + UUID.nameUUIDFromBytes(idempotencyKey.getBytes()), null);
    }

    @Override
    @CircuitBreaker(name = "paymentGateway")
    @Retry(name = "paymentGateway")
    public RefundResult refund(String gatewayReference, BigDecimal amount) {
        return new RefundResult(true, "ref_" + gatewayReference);
    }
}
