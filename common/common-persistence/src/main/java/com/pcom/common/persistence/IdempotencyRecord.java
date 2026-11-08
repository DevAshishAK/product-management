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
@Table(name = "idempotency_record")
public class IdempotencyRecord {
    @Id
    private UUID id;
    private String scope;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    private String requestHash;
    @Column(columnDefinition = "text")
    private String responseBody;
    private Instant createdAt;

    public IdempotencyRecord(String scope, String key, String requestHash, String responseBody) {
        this.id = UUID.randomUUID();
        this.scope = scope;
        this.idempotencyKey = key;
        this.requestHash = requestHash;
        this.responseBody = responseBody;
        this.createdAt = Instant.now();
    }
}
