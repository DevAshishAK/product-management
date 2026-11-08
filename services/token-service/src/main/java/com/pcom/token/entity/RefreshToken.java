package com.pcom.token.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    @Id
    private UUID id = UUID.randomUUID();
    private String tokenHash;
    private String subject;
    private String clientId;
    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt = Instant.now();
}
