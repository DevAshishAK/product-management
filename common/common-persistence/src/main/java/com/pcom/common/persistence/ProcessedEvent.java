package com.pcom.common.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {
    @Id
    private String id;
    private Instant processedAt;

    public ProcessedEvent(String id) {
        this.id = id;
        this.processedAt = Instant.now();
    }
}
