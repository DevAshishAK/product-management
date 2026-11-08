package com.pcom.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pcom.audit.domain.AuditRecord;
import com.pcom.audit.domain.AuditRepository;
import com.pcom.audit.service.AuditRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AuditRecorderTest {
    AuditRepository repo = Mockito.mock(AuditRepository.class);
    AuditRecorder recorder = new AuditRecorder(repo, new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void storesEventUsingEventIdAsDocumentId() {
        String json = "{\"eventId\":\"11111111-1111-1111-1111-111111111111\",\"eventType\":\"ORDER_CREATED\",\"version\":1,"
                + "\"occurredAt\":\"2026-01-01T00:00:00Z\",\"correlationId\":\"c1\",\"aggregateId\":\"o1\",\"payload\":{\"a\":1}}";
        recorder.record(json, "order-events", 2, 42L);
        ArgumentCaptor<AuditRecord> c = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repo).save(c.capture());
        assertThat(c.getValue().eventId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(c.getValue().payload()).contains("\"a\"");
        assertThat(c.getValue().offset()).isEqualTo(42L);
    }

    @Test
    void malformedMessagesAreNonRetryable() {
        assertThatThrownBy(() -> recorder.record("not json", "t", 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
