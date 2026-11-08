package com.pcom.invoice.messaging;

import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.event.Topics;
import com.pcom.common.persistence.EventProcessor;
import com.pcom.invoice.service.InvoiceService;
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
public class InvoiceEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(InvoiceEventConsumer.class);
    static final String CONSUMER = "invoice-service";
    private final EventProcessor processor;
    private final EventSerde serde;
    private final InvoiceService invoices;

    public InvoiceEventConsumer(EventProcessor processor, EventSerde serde, InvoiceService invoices) {
        this.processor = processor;
        this.serde = serde;
        this.invoices = invoices;
    }

    @RetryableTopic(attempts = "4", numPartitions = "6", dltStrategy = DltStrategy.FAIL_ON_ERROR,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
            exclude = {IllegalArgumentException.class})
    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = CONSUMER)
    public void onPaymentEvent(String message) {
        processor.process(CONSUMER, message, this::handle);
    }

    @DltHandler
    public void dlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event moved to dead-letter topic {}: {}", topic, message);
    }

    void handle(EventEnvelope event) {
        if (serde.type(event).orElse(null) == EventType.PAYMENT_COMPLETED) {
            invoices.generateForOrder(serde.payload(event, Events.PaymentCompleted.class).orderId());
        }
    }
}
