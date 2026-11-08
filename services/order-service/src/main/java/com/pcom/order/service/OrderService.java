package com.pcom.order.service;

import com.pcom.common.core.BusinessException;
import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.order.OrderView;
import com.pcom.common.dto.product.ProductSnapshot;
import com.pcom.common.persistence.OutboxService;
import com.pcom.order.client.ProductGateway;
import com.pcom.order.entity.OrderEntity;
import com.pcom.order.entity.OrderItem;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.entity.OrderStatusHistory;
import com.pcom.order.repository.OrderRepository;
import com.pcom.order.repository.OrderStatusHistoryRepository;
import com.pcom.order.web.OrderDtos.OrderResponse;
import com.pcom.order.web.OrderDtos.OrderStatusResponse;
import com.pcom.order.web.OrderDtos.PlaceOrderRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ProductGateway products;
    private final OutboxService outbox;
    private final Counter created;
    private final Counter cancelled;

    public OrderService(OrderRepository orders, OrderStatusHistoryRepository history, ProductGateway products,
                        OutboxService outbox, MeterRegistry meters) {
        this.orders = orders;
        this.history = history;
        this.products = products;
        this.outbox = outbox;
        this.created = Counter.builder("orders.created").description("Orders placed").register(meters);
        this.cancelled = Counter.builder("orders.cancelled").description("Orders cancelled").register(meters);
    }

    @Transactional
    public OrderResponse place(UUID userId, PlaceOrderRequest request) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        request.items().forEach(l -> quantities.merge(l.productId(), l.quantity(), Integer::sum));
        Map<UUID, ProductSnapshot> catalog = products.lookup(quantities.keySet()).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));

        OrderEntity order = new OrderEntity();
        order.setUserId(userId);
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;
        for (Map.Entry<UUID, Integer> e : quantities.entrySet()) {
            ProductSnapshot p = catalog.get(e.getKey());
            if (p == null || !p.active()) {
                throw new BusinessException("PRODUCT_UNAVAILABLE", "Product not available: " + e.getKey(), HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (currency == null) currency = p.currency();
            if (!currency.equals(p.currency())) {
                throw new BusinessException("MIXED_CURRENCY", "All items must use the same currency", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            order.getItems().add(new OrderItem(p.id(), p.name(), p.price(), e.getValue()));
            total = total.add(p.price().multiply(BigDecimal.valueOf(e.getValue())));
        }
        order.setTotalAmount(total);
        order.setCurrency(currency);
        order = orders.save(order);
        history.save(new OrderStatusHistory(order.getId(), OrderStatus.PENDING, "Order placed"));

        List<Events.OrderLine> lines = order.getItems().stream()
                .map(i -> new Events.OrderLine(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity())).toList();
        outbox.publish(EventType.ORDER_CREATED, order.getId(),
                new Events.OrderCreated(order.getId(), userId, lines, total, currency));
        created.increment();
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, UUID requester, boolean privileged) {
        return OrderResponse.from(findAuthorized(id, requester, privileged));
    }

    @Transactional(readOnly = true)
    public OrderStatusResponse status(UUID id, UUID requester, boolean privileged) {
        OrderEntity o = findAuthorized(id, requester, privileged);
        return OrderStatusResponse.of(o, history.findByOrderIdOrderByChangedAtAsc(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(UUID userId, Collection<OrderStatus> statuses, Pageable pageable) {
        Page<OrderEntity> page = (statuses == null || statuses.isEmpty())
                ? orders.findByUserId(userId, pageable) : orders.findByUserIdAndStatusIn(userId, statuses, pageable);
        return page.map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderView view(UUID id) {
        OrderEntity o = orders.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
        return new OrderView(o.getId(), o.getUserId(), o.getStatus().name(), o.getTotalAmount(), o.getCurrency(), o.getCreatedAt(),
                o.getItems().stream().map(i -> new OrderView.OrderLineView(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity())).toList());
    }

    @Transactional
    public OrderResponse cancel(UUID id, UUID requester, boolean privileged, String reason) {
        OrderEntity o = findAuthorized(id, requester, privileged);
        if (!o.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BusinessException("ORDER_NOT_CANCELLABLE", "Order in status " + o.getStatus() + " cannot be cancelled", HttpStatus.CONFLICT);
        }
        cancelInternal(o, reason == null || reason.isBlank() ? "Cancelled by customer" : reason);
        return OrderResponse.from(o);
    }

    // ----- saga reactions (invoked inside the idempotent event-processing transaction) -----

    @Transactional
    public void onInventoryReserved(UUID orderId) {
        transition(orderId, OrderStatus.INVENTORY_RESERVED, "Inventory reserved");
    }

    @Transactional
    public void onPaymentCompleted(UUID orderId) {
        OrderEntity o = orders.findById(orderId).orElse(null);
        if (o == null) return;
        if (o.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment completed for cancelled order {} - payment-service will refund via ORDER_CANCELLED", orderId);
            return;
        }
        if (applyTransition(o, OrderStatus.CONFIRMED, "Payment completed")) {
            outbox.publish(EventType.ORDER_CONFIRMED, o.getId(), new Events.OrderConfirmed(o.getId(), o.getUserId()));
        }
    }

    /** Compensation trigger: payment failure or stock shortage cancels the order. */
    @Transactional
    public void cancelBySystem(UUID orderId, String reason) {
        orders.findById(orderId).filter(o -> o.getStatus().canTransitionTo(OrderStatus.CANCELLED))
                .ifPresent(o -> cancelInternal(o, reason));
    }

    private void cancelInternal(OrderEntity o, String reason) {
        o.setStatus(OrderStatus.CANCELLED);
        o.setCancelReason(reason);
        history.save(new OrderStatusHistory(o.getId(), OrderStatus.CANCELLED, reason));
        outbox.publish(EventType.ORDER_CANCELLED, o.getId(), new Events.OrderCancelled(o.getId(), o.getUserId(), reason));
        cancelled.increment();
    }

    private void transition(UUID orderId, OrderStatus next, String reason) {
        orders.findById(orderId).ifPresent(o -> applyTransition(o, next, reason));
    }

    private boolean applyTransition(OrderEntity o, OrderStatus next, String reason) {
        if (!o.getStatus().canTransitionTo(next)) {
            log.info("Ignoring transition {} -> {} for order {}", o.getStatus(), next, o.getId());
            return false;
        }
        o.setStatus(next);
        history.save(new OrderStatusHistory(o.getId(), next, reason));
        return true;
    }

    private OrderEntity findAuthorized(UUID id, UUID requester, boolean privileged) {
        OrderEntity o = orders.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
        if (!privileged && !o.getUserId().equals(requester)) throw new NotFoundException("Order", id);
        return o;
    }
}
