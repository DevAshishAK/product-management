package com.pcom.notification.web;

import com.pcom.common.core.PageResponse;
import com.pcom.common.security.CurrentUser;
import com.pcom.notification.entity.Notification;
import com.pcom.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    public record NotificationResponse(UUID id, String channel, String template, String subject, String body,
                                       String status, Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getChannel().name(), n.getTemplate(), n.getSubject(),
                    n.getBody(), n.getStatus(), n.getCreatedAt());
        }
    }

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('notification:read')")
    public PageResponse<NotificationResponse> mine(Pageable pageable) {
        return PageResponse.of(notifications.forUser(CurrentUser.id(), pageable), NotificationResponse::from);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public PageResponse<NotificationResponse> forUser(@PathVariable UUID userId, Pageable pageable) {
        return PageResponse.of(notifications.forUser(userId, pageable), NotificationResponse::from);
    }
}
