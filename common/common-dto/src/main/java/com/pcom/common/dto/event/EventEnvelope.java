package com.pcom.common.dto.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Versioned wrapper around every event published to Kafka. */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        JsonNode payload) {}
