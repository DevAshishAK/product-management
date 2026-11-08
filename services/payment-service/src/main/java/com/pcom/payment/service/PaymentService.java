package com.pcom.payment.service;

import com.pcom.common.core.BusinessException;
import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.persistence.OutboxService;
import com.pcom.payment.entity.Payment;
import com.pcom.payment.entity.PaymentStatus;
import com.pcom.payment.gateway.PaymentGateway;
import com.pcom.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxService outbox;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, OutboxService outbox) {
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
    }

    /** One payment per order (unique order_id): safe to call twice for the same order. */
    @Transactional
    public Payment initiate(UUID orderId, UUID userId, BigDecimal amount, String currency) {
        Payment existing = payments.findByOrderId(orderId).orElse(null);
        if (existing != null) return existing; // duplicate request, or order cancelled before payment (tombstone)

        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setUserId(userId);
        p.setAmount(amount);
        p.setCurrency(currency);
        p = payments.save(p);
        outbox.publish(EventType.PAYMENT_INITIATED, orderId, new Events.PaymentInitiated(p.getId(), orderId, userId, amount, currency));

        PaymentGateway.ChargeResult result = gateway.charge(orderId.toString(), amount, currency);
        if (result.success()) {
            p.setStatus(PaymentStatus.COMPLETED);
            p.setGatewayRef(result.reference());
            outbox.publish(EventType.PAYMENT_COMPLETED, orderId,
                    new Events.PaymentCompleted(p.getId(), orderId, userId, amount, currency, result.reference()));
        } else {
            p.setStatus(PaymentStatus.FAILED);
            p.setFailureReason(result.failureReason());
            outbox.publish(EventType.PAYMENT_FAILED, orderId,
                    new Events.PaymentFailed(p.getId(), orderId, userId, result.failureReason()));
        }
        return p;
    }

    @Transactional
    public Payment refund(UUID paymentId, BigDecimal amount, String reason) {
        Payment p = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));
        return doRefund(p, amount, reason);
    }

    /** Saga compensation for ORDER_CANCELLED. */
    @Transactional
    public void refundForCancelledOrder(UUID orderId, UUID userId, String reason) {
        Payment p = payments.findByOrderId(orderId).orElse(null);
        if (p == null) {
            // Cancellation arrived before payment: tombstone prevents a later charge.
            Payment tombstone = new Payment();
            tombstone.setOrderId(orderId);
            tombstone.setUserId(userId);
            tombstone.setAmount(BigDecimal.ZERO);
            tombstone.setCurrency("N/A");
            tombstone.setStatus(PaymentStatus.CANCELLED);
            payments.save(tombstone);
            return;
        }
        if (p.getStatus() == PaymentStatus.COMPLETED || p.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            doRefund(p, null, reason);
        }
    }

    @Transactional(readOnly = true)
    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> new NotFoundException("Payment", id));
    }

    @Transactional(readOnly = true)
    public Payment getByOrder(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("Payment for order", orderId));
    }

    private Payment doRefund(Payment p, BigDecimal requested, String reason) {
        if (p.getStatus() != PaymentStatus.COMPLETED && p.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException("NOT_REFUNDABLE", "Payment in status " + p.getStatus() + " cannot be refunded", HttpStatus.CONFLICT);
        }
        BigDecimal remaining = p.getAmount().subtract(p.getRefundedAmount());
        BigDecimal amount = requested == null ? remaining : requested;
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException("REFUND_EXCEEDS_PAYMENT", "Refund exceeds refundable amount " + remaining, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        PaymentGateway.RefundResult r = gateway.refund(p.getGatewayRef(), amount);
        if (!r.success()) throw new BusinessException("REFUND_FAILED", "Gateway rejected the refund", HttpStatus.BAD_GATEWAY);
        p.setRefundedAmount(p.getRefundedAmount().add(amount));
        p.setStatus(p.getRefundedAmount().compareTo(p.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        outbox.publish(EventType.PAYMENT_REFUNDED, p.getOrderId(),
                new Events.PaymentRefunded(p.getId(), p.getOrderId(), p.getUserId(), amount, reason));
        return p;
    }
}
