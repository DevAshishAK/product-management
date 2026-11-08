package com.pcom.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcom.common.core.BusinessException;
import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.product.ProductSnapshot;
import com.pcom.common.persistence.OutboxService;
import com.pcom.order.client.ProductGateway;
import com.pcom.order.entity.OrderEntity;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.repository.OrderRepository;
import com.pcom.order.repository.OrderStatusHistoryRepository;
import com.pcom.order.service.OrderService;
import com.pcom.order.web.OrderDtos.OrderResponse;
import com.pcom.order.web.OrderDtos.PlaceOrderRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class OrderServiceTest {
    OrderRepository orders = Mockito.mock(OrderRepository.class);
    OrderStatusHistoryRepository history = Mockito.mock(OrderStatusHistoryRepository.class);
    ProductGateway products = Mockito.mock(ProductGateway.class);
    OutboxService outbox = Mockito.mock(OutboxService.class);
    OrderService service;
    UUID userId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, history, products, outbox, new SimpleMeterRegistry());
        when(orders.save(any(OrderEntity.class))).thenAnswer(inv -> {
            OrderEntity o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", UUID.randomUUID());
            return o;
        });
    }

    private OrderEntity order(OrderStatus status) {
        OrderEntity o = new OrderEntity();
        ReflectionTestUtils.setField(o, "id", UUID.randomUUID());
        o.setUserId(userId);
        o.setStatus(status);
        o.setTotalAmount(BigDecimal.TEN);
        o.setCurrency("USD");
        when(orders.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    @Test
    void placeOrderUsesCatalogPricesAndPublishesOrderCreated() {
        when(products.lookup(any())).thenReturn(List.of(new ProductSnapshot(productId, "SKU", "Widget", new BigDecimal("12.50"), "USD", true)));
        OrderResponse r = service.place(userId, new PlaceOrderRequest(List.of(
                new PlaceOrderRequest.Line(productId, 2), new PlaceOrderRequest.Line(productId, 1))));
        assertThat(r.totalAmount()).isEqualByComparingTo("37.50");
        assertThat(r.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(r.items()).hasSize(1);
        verify(outbox).publish(eq(EventType.ORDER_CREATED), any(UUID.class), any());
    }

    @Test
    void placeOrderRejectsInactiveProduct() {
        when(products.lookup(any())).thenReturn(List.of(new ProductSnapshot(productId, "SKU", "Widget", BigDecimal.ONE, "USD", false)));
        assertThatThrownBy(() -> service.place(userId, new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line(productId, 1)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not available");
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    void placeOrderRejectsUnknownProduct() {
        when(products.lookup(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.place(userId, new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line(productId, 1)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void customerCannotSeeSomeoneElsesOrder() {
        OrderEntity o = order(OrderStatus.PENDING);
        assertThatThrownBy(() -> service.get(o.getId(), UUID.randomUUID(), false)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(o.getId(), UUID.randomUUID(), true).id()).isEqualTo(o.getId());
    }

    @Test
    void cancelPublishesOrderCancelled() {
        OrderEntity o = order(OrderStatus.INVENTORY_RESERVED);
        OrderResponse r = service.cancel(o.getId(), userId, false, "changed my mind");
        assertThat(r.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox).publish(eq(EventType.ORDER_CANCELLED), eq(o.getId()), any());
    }

    @Test
    void cancelledOrderCannotBeCancelledAgain() {
        OrderEntity o = order(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.cancel(o.getId(), userId, false, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void paymentCompletedConfirmsOrder() {
        OrderEntity o = order(OrderStatus.INVENTORY_RESERVED);
        service.onPaymentCompleted(o.getId());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outbox).publish(eq(EventType.ORDER_CONFIRMED), eq(o.getId()), any());
    }

    @Test
    void paymentCompletedForCancelledOrderIsIgnored() {
        OrderEntity o = order(OrderStatus.CANCELLED);
        service.onPaymentCompleted(o.getId());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    void inventoryReservedAfterConfirmationIsIgnored() {
        OrderEntity o = order(OrderStatus.CONFIRMED);
        service.onInventoryReserved(o.getId());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void systemCancellationOnPaymentFailure() {
        OrderEntity o = order(OrderStatus.INVENTORY_RESERVED);
        service.cancelBySystem(o.getId(), "Payment failed: declined");
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o.getCancelReason()).contains("declined");
    }
}
