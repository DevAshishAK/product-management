package com.pcom.notification.entity;

import com.pcom.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification")
public class Notification extends AuditableEntity {
    private UUID userId;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    private String template;
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    private String status;
    private String error;
}
