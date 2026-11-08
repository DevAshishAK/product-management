package com.pcom.common.dto.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class EventSerde {
    private final ObjectMapper mapper;

    public EventSerde(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public EventEnvelope envelope(EventType type, String aggregateId, String correlationId, Object payload) {
        return new EventEnvelope(UUID.randomUUID(), type.name(), type.version(), Instant.now(),
                correlationId, aggregateId, mapper.valueToTree(payload));
    }

    public String write(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + envelope.eventId(), e);
        }
    }

    /** Malformed messages raise IllegalArgumentException, which listeners treat as non-retryable (poison pill). */
    public EventEnvelope read(String json) {
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event envelope", e);
        }
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        try {
            return mapper.treeToValue(envelope.payload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed payload for " + envelope.eventType(), e);
        }
    }

    /** Unknown types are ignored by consumers (forward compatibility). */
    public Optional<EventType> type(EventEnvelope envelope) {
        try {
            return Optional.of(EventType.valueOf(envelope.eventType()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
