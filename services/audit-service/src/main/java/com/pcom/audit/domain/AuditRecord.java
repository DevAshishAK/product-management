package com.pcom.audit.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Immutable audit trail entry; _id = eventId makes ingestion naturally idempotent. */
@Document("audit_events")
public record AuditRecord(
        @Id String eventId,
        @Indexed String eventType,
        String topic,
        @Indexed String aggregateId,
        String correlationId,
        Instant occurredAt,
        Instant receivedAt,
        int partition,
        long offset,
        String payload) {}
