package com.pcom.payment.web;

import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.PlatformHeaders;
import com.pcom.common.persistence.IdempotencyService;
import com.pcom.common.security.CurrentUser;
import com.pcom.payment.entity.Payment;
import com.pcom.payment.service.PaymentService;
import com.pcom.payment.web.PaymentDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {
    private final PaymentService payments;
    private final IdempotencyService idempotency;

    public PaymentController(PaymentService payments, IdempotencyService idempotency) {
        this.payments = payments;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Manually initiate a payment (normally saga-driven). Idempotent.")
    @PostMapping
    @PreAuthorize("hasAuthority('payment:write')")
    public ResponseEntity<PaymentResponse> initiate(@RequestHeader(PlatformHeaders.IDEMPOTENCY_KEY) String key,
                                                    @Valid @RequestBody InitiatePaymentRequest request) {
        var result = idempotency.execute("payment.initiate", key, request, PaymentResponse.class,
                () -> PaymentResponse.from(payments.initiate(request.orderId(), request.userId(), request.amount(), request.currency().toUpperCase())));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(result.replayed())).body(result.body());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:read')")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(authorize(payments.get(id)));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('payment:read')")
    public PaymentResponse byOrder(@PathVariable UUID orderId) {
        return PaymentResponse.from(authorize(payments.getByOrder(orderId)));
    }

    @Operation(summary = "Refund (full or partial). Idempotent.")
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID id,
                                                  @RequestHeader(PlatformHeaders.IDEMPOTENCY_KEY) String key,
                                                  @Valid @RequestBody RefundRequest request) {
        var result = idempotency.execute("payment.refund." + id, key, request, PaymentResponse.class,
                () -> PaymentResponse.from(payments.refund(id, request.amount(), request.reason())));
        return ResponseEntity.ok().header("Idempotency-Replayed", String.valueOf(result.replayed())).body(result.body());
    }

    private Payment authorize(Payment p) {
        if (!CurrentUser.isPrivileged() && !p.getUserId().equals(CurrentUser.id())) {
            throw new NotFoundException("Payment", p.getId());
        }
        return p;
    }
}
