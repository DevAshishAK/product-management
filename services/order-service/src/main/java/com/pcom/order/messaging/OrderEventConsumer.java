package com.pcom.order.messaging;

import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.event.Topics;
import com.pcom.common.persistence.EventProcessor;
import com.pcom.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/** Choreography participant: reacts to inventory and payment outcomes. */
@Component
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    static final String CONSUMER = "order-service";
    private final EventProcessor processor;
    private final EventSerde serde;
    private final OrderService orders;

    public OrderEventConsumer(EventProcessor processor, EventSerde serde, OrderService orders) {
        this.processor = processor;
        this.serde = serde;
        this.orders = orders;
    }

    @RetryableTopic(attempts = "4", numPartitions = "6", dltStrategy = DltStrategy.FAIL_ON_ERROR,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            exclude = {IllegalArgumentException.class})
    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS}, groupId = CONSUMER)
    public void onEvent(String message) {
        processor.process(CONSUMER, message, this::handle);
    }

    @DltHandler
    public void dlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event moved to dead-letter topic {}: {}", topic, message);
    }

    void handle(EventEnvelope event) {
        EventType type = serde.type(event).orElse(null);
        if (type == null) return;
        switch (type) {
            case INVENTORY_RESERVED -> orders.onInventoryReserved(serde.payload(event, Events.InventoryReserved.class).orderId());
            case INVENTORY_RESERVATION_FAILED -> {
                Events.InventoryReservationFailed f = serde.payload(event, Events.InventoryReservationFailed.class);
                orders.cancelBySystem(f.orderId(), f.reason());
            }
            case PAYMENT_COMPLETED -> orders.onPaymentCompleted(serde.payload(event, Events.PaymentCompleted.class).orderId());
            case PAYMENT_FAILED -> {
                Events.PaymentFailed f = serde.payload(event, Events.PaymentFailed.class);
                orders.cancelBySystem(f.orderId(), "Payment failed: " + f.reason());
            }
            default -> { }
        }
    }
}
