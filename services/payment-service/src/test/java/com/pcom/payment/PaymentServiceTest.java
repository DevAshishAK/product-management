package com.pcom.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.persistence.OutboxService;
import com.pcom.payment.entity.Payment;
import com.pcom.payment.entity.PaymentStatus;
import com.pcom.payment.gateway.PaymentGateway;
import com.pcom.payment.repository.PaymentRepository;
import com.pcom.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentServiceTest {
    PaymentRepository payments = Mockito.mock(PaymentRepository.class);
    PaymentGateway gateway = Mockito.mock(PaymentGateway.class);
    OutboxService outbox = Mockito.mock(OutboxService.class);
    PaymentService service;
    UUID orderId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, gateway, outbox);
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void successfulChargeCompletesPayment() {
        when(gateway.charge(any(), any(), any())).thenReturn(new PaymentGateway.ChargeResult(true, "ref1", null));
        Payment p = service.initiate(orderId, userId, BigDecimal.TEN, "USD");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(outbox).publish(eq(EventType.PAYMENT_INITIATED), eq(orderId), any());
        verify(outbox).publish(eq(EventType.PAYMENT_COMPLETED), eq(orderId), any());
    }

    @Test
    void declinedChargeFailsPayment() {
        when(gateway.charge(any(), any(), any())).thenReturn(new PaymentGateway.ChargeResult(false, null, "declined"));
        Payment p = service.initiate(orderId, userId, BigDecimal.TEN, "USD");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(outbox).publish(eq(EventType.PAYMENT_FAILED), eq(orderId), any());
    }

    @Test
    void secondInitiationForSameOrderDoesNotChargeAgain() {
        Payment existing = new Payment();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(existing));
        assertThat(service.initiate(orderId, userId, BigDecimal.TEN, "USD")).isSameAs(existing);
        verify(gateway, never()).charge(any(), any(), any());
    }

    private Payment completed() {
        Payment p = new Payment();
        p.setOrderId(orderId); p.setUserId(userId); p.setAmount(new BigDecimal("100.00")); p.setCurrency("USD");
        p.setStatus(PaymentStatus.COMPLETED); p.setGatewayRef("ref1");
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(p));
        when(gateway.refund(any(), any())).thenReturn(new PaymentGateway.RefundResult(true, "r"));
        return p;
    }

    @Test
    void cancelledOrderTriggersFullRefund() {
        Payment p = completed();
        service.refundForCancelledOrder(orderId, userId, "cancelled");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(outbox).publish(eq(EventType.PAYMENT_REFUNDED), eq(orderId), any());
    }

    @Test
    void cancellationBeforePaymentCreatesTombstone() {
        when(payments.findByOrderId(orderId)).thenReturn(Optional.empty());
        service.refundForCancelledOrder(orderId, userId, "cancelled");
        verify(payments).save(any(Payment.class));
        verify(gateway, never()).refund(any(), any());
    }

    @Test
    void partialRefundsAreCappedAtPaidAmount() {
        Payment p = completed();
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(p));
        assertThat(service.refund(id, new BigDecimal("40"), "partial").getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThatThrownBy(() -> service.refund(id, new BigDecimal("70"), "too much")).isInstanceOf(BusinessException.class);
    }
}
