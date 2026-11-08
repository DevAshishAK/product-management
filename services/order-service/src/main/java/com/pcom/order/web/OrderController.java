package com.pcom.order.web;

import com.pcom.common.core.PageResponse;
import com.pcom.common.dto.PlatformHeaders;
import com.pcom.common.dto.order.OrderView;
import com.pcom.common.persistence.IdempotencyService;
import com.pcom.common.security.CurrentUser;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.service.OrderService;
import com.pcom.order.web.OrderDtos.*;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Orders")
public class OrderController {
    private final OrderService orders;
    private final IdempotencyService idempotency;

    public OrderController(OrderService orders, IdempotencyService idempotency) {
        this.orders = orders;
        this.idempotency = idempotency;
    }

    @Operation(summary = "Place an order (idempotent: Idempotency-Key header required)")
    @PostMapping("/api/v1/orders")
    @PreAuthorize("hasAuthority('order:write')")
    public ResponseEntity<OrderResponse> place(@RequestHeader(PlatformHeaders.IDEMPOTENCY_KEY) String key,
                                               @Valid @RequestBody PlaceOrderRequest request) {
        UUID userId = CurrentUser.id();
        var result = idempotency.execute("order.place", userId + ":" + key, request, OrderResponse.class,
                () -> orders.place(userId, request));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }

    @Operation(summary = "My orders (optionally filtered by status)")
    @GetMapping("/api/v1/orders")
    @PreAuthorize("hasAuthority('order:read')")
    public PageResponse<OrderResponse> list(@RequestParam(required = false) Set<OrderStatus> status, Pageable pageable) {
        return PageResponse.of(orders.list(CurrentUser.id(), status, pageable), o -> o);
    }

    @Operation(summary = "Order history: completed (confirmed or cancelled) orders")
    @GetMapping("/api/v1/orders/history")
    @PreAuthorize("hasAuthority('order:read')")
    public PageResponse<OrderResponse> history(Pageable pageable) {
        return PageResponse.of(orders.list(CurrentUser.id(), Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED), pageable), o -> o);
    }

    @GetMapping("/api/v1/orders/{id}")
    @PreAuthorize("hasAuthority('order:read')")
    public OrderResponse get(@PathVariable UUID id) {
        return orders.get(id, CurrentUser.id(), CurrentUser.isPrivileged());
    }

    @Operation(summary = "Order status tracking with transition history")
    @GetMapping("/api/v1/orders/{id}/status")
    @PreAuthorize("hasAuthority('order:read')")
    public OrderStatusResponse status(@PathVariable UUID id) {
        return orders.status(id, CurrentUser.id(), CurrentUser.isPrivileged());
    }

    @PostMapping("/api/v1/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    public OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest request) {
        return orders.cancel(id, CurrentUser.id(), CurrentUser.isPrivileged(), request == null ? null : request.reason());
    }

    @Hidden
    @GetMapping("/internal/orders/{id}")
    @PreAuthorize("hasRole('SERVICE')")
    public OrderView internal(@PathVariable UUID id) {
        return orders.view(id);
    }
}
