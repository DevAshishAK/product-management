package com.pcom.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcom.common.dto.event.EventEnvelope;
import com.pcom.audit.domain.AuditRecord;
import com.pcom.audit.domain.AuditRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditRecorder {
    private final AuditRepository repository;
    private final ObjectMapper mapper;

    public AuditRecorder(AuditRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public void record(String json, String topic, int partition, long offset) {
        EventEnvelope e;
        try {
            e = mapper.readValue(json, EventEnvelope.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed event envelope", ex); // non-retryable
        }
        repository.save(new AuditRecord(e.eventId().toString(), e.eventType(), topic, e.aggregateId(), e.correlationId(),
                e.occurredAt(), Instant.now(), partition, offset, e.payload() == null ? null : e.payload().toString()));
    }
}
