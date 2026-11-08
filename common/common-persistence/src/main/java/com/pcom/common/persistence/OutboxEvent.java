package com.pcom.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id
    private UUID id;
    private String aggregateId;
    private String topic;
    private String eventKey;
    private String eventType;
    @Column(columnDefinition = "text")
    private String payload;
    private boolean published;
    private Instant createdAt;
    private Instant publishedAt;

    public OutboxEvent(UUID id, String topic, String aggregateId, String eventType, String payload) {
        this.id = id;
        this.topic = topic;
        this.aggregateId = aggregateId;
        this.eventKey = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }
}
