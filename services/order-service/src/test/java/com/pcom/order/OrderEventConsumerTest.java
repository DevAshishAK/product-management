package com.pcom.order;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.persistence.EventProcessor;
import com.pcom.order.messaging.OrderEventConsumer;
import com.pcom.order.service.OrderService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderEventConsumerTest {
    EventSerde serde = new EventSerde(new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    OrderService orders = Mockito.mock(OrderService.class);
    OrderEventConsumer consumer = new OrderEventConsumer(Mockito.mock(EventProcessor.class), serde, orders);
    UUID orderId = UUID.randomUUID();

    @Test
    void paymentFailedCancelsOrder() {
        EventEnvelope e = serde.envelope(EventType.PAYMENT_FAILED, orderId.toString(), "c1",
                new Events.PaymentFailed(UUID.randomUUID(), orderId, UUID.randomUUID(), "declined"));
        consumer.handle(serde.read(serde.write(e)));
        verify(orders).cancelBySystem(orderId, "Payment failed: declined");
    }

    @Test
    void paymentCompletedConfirmsOrder() {
        EventEnvelope e = serde.envelope(EventType.PAYMENT_COMPLETED, orderId.toString(), "c1",
                new Events.PaymentCompleted(UUID.randomUUID(), orderId, UUID.randomUUID(), BigDecimal.TEN, "USD", "ref"));
        consumer.handle(serde.read(serde.write(e)));
        verify(orders).onPaymentCompleted(orderId);
    }

    @Test
    void inventoryReservedMovesOrderForward() {
        EventEnvelope e = serde.envelope(EventType.INVENTORY_RESERVED, orderId.toString(), "c1",
                new Events.InventoryReserved(orderId, UUID.randomUUID(), BigDecimal.TEN, "USD"));
        consumer.handle(serde.read(serde.write(e)));
        verify(orders).onInventoryReserved(orderId);
    }

    @Test
    void reservationFailureCancelsOrder() {
        EventEnvelope e = serde.envelope(EventType.INVENTORY_RESERVATION_FAILED, orderId.toString(), "c1",
                new Events.InventoryReservationFailed(orderId, UUID.randomUUID(), "no stock"));
        consumer.handle(serde.read(serde.write(e)));
        verify(orders).cancelBySystem(orderId, "no stock");
    }
}
