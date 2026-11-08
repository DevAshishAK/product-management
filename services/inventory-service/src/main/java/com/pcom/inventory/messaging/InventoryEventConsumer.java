package com.pcom.inventory.messaging;

import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.event.Topics;
import com.pcom.common.persistence.EventProcessor;
import com.pcom.inventory.service.InventoryService;
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

@Component
public class InventoryEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);
    static final String CONSUMER = "inventory-service";
    private final EventProcessor processor;
    private final EventSerde serde;
    private final InventoryService inventory;

    public InventoryEventConsumer(EventProcessor processor, EventSerde serde, InventoryService inventory) {
        this.processor = processor;
        this.serde = serde;
        this.inventory = inventory;
    }

    @RetryableTopic(attempts = "4", numPartitions = "6", dltStrategy = DltStrategy.FAIL_ON_ERROR,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            exclude = {IllegalArgumentException.class})
    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = CONSUMER)
    public void onOrderEvent(String message) {
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
            case ORDER_CREATED -> inventory.reserveForOrder(serde.payload(event, Events.OrderCreated.class));
            case ORDER_CONFIRMED -> inventory.commit(serde.payload(event, Events.OrderConfirmed.class).orderId());
            case ORDER_CANCELLED -> {
                Events.OrderCancelled c = serde.payload(event, Events.OrderCancelled.class);
                inventory.releaseForOrder(c.orderId(), c.reason());
            }
            default -> { }
        }
    }
}
