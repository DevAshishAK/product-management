package com.pcom.notification.messaging;

import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.event.Topics;
import com.pcom.common.persistence.EventProcessor;
import com.pcom.notification.service.NotificationService;
import com.pcom.notification.service.NotificationTemplate;
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
public class NotificationEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);
    static final String CONSUMER = "notification-service";
    private final EventProcessor processor;
    private final EventSerde serde;
    private final NotificationService notifications;

    public NotificationEventConsumer(EventProcessor processor, EventSerde serde, NotificationService notifications) {
        this.processor = processor;
        this.serde = serde;
        this.notifications = notifications;
    }

    @RetryableTopic(attempts = "4", numPartitions = "6", dltStrategy = DltStrategy.FAIL_ON_ERROR,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            exclude = {IllegalArgumentException.class})
    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS, Topics.INVOICE_EVENTS}, groupId = CONSUMER)
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
            case ORDER_CONFIRMED -> {
                Events.OrderConfirmed e = serde.payload(event, Events.OrderConfirmed.class);
                notifications.notifyUser(e.userId(), NotificationTemplate.ORDER_CONFIRMED, e.orderId());
            }
            case ORDER_CANCELLED -> {
                Events.OrderCancelled e = serde.payload(event, Events.OrderCancelled.class);
                notifications.notifyUser(e.userId(), NotificationTemplate.ORDER_CANCELLED, e.orderId(), e.reason());
            }
            case PAYMENT_COMPLETED -> {
                Events.PaymentCompleted e = serde.payload(event, Events.PaymentCompleted.class);
                notifications.notifyUser(e.userId(), NotificationTemplate.PAYMENT_COMPLETED, e.orderId());
            }
            case PAYMENT_FAILED -> {
                Events.PaymentFailed e = serde.payload(event, Events.PaymentFailed.class);
                notifications.notifyUser(e.userId(), NotificationTemplate.PAYMENT_FAILED, e.orderId(), e.reason());
            }
            case PAYMENT_REFUNDED -> {
                Events.PaymentRefunded e = serde.payload(event, Events.PaymentRefunded.class);
                notifications.notifyUser(e.userId(), NotificationTemplate.PAYMENT_REFUNDED, e.orderId());
            }
            case INVOICE_GENERATED -> {
                Events.InvoiceGenerated e = serde.payload(event, Events.InvoiceGenerated.class);
                notifications.notifyUser(e.userId(), NotificationTemplate.INVOICE_READY, e.invoiceNumber(), e.orderId());
            }
            default -> { }
        }
    }
}
