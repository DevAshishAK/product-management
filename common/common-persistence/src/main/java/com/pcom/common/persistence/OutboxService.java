package com.pcom.common.persistence;

import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional outbox: events are stored in the same DB transaction as the state change, then relayed to Kafka. */
public class OutboxService {
    private final OutboxRepository repository;
    private final EventSerde serde;

    public OutboxService(OutboxRepository repository, EventSerde serde) {
        this.repository = repository;
        this.serde = serde;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publish(EventType type, UUID aggregateId, Object payload) {
        EventEnvelope envelope = serde.envelope(type, aggregateId.toString(), MDC.get("correlationId"), payload);
        repository.save(new OutboxEvent(envelope.eventId(), type.topic(), aggregateId.toString(), type.name(), serde.write(envelope)));
        return envelope.eventId();
    }
}
