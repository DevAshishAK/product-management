package com.pcom.common.persistence;

import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer: the dedup marker and the business changes commit atomically, which gives
 * effectively-once processing on top of Kafka's at-least-once delivery.
 */
public class EventProcessor {
    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);
    private final ProcessedEventRepository processed;
    private final EventSerde serde;

    public EventProcessor(ProcessedEventRepository processed, EventSerde serde) {
        this.processed = processed;
        this.serde = serde;
    }

    @Transactional
    public void process(String consumer, String json, Consumer<EventEnvelope> handler) {
        EventEnvelope event = serde.read(json);
        if (event.correlationId() != null) MDC.put("correlationId", event.correlationId());
        try {
            String id = consumer + ":" + event.eventId();
            if (processed.existsById(id)) {
                log.info("Skipping duplicate event {} ({})", event.eventId(), event.eventType());
                return;
            }
            processed.save(new ProcessedEvent(id));
            handler.accept(event);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
