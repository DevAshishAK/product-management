package com.pcom.notification.service;

import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.user.UserContact;
import com.pcom.common.persistence.OutboxService;
import com.pcom.notification.channel.ChannelSender;
import com.pcom.notification.client.UserGateway;
import com.pcom.notification.entity.Notification;
import com.pcom.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notifications;
    private final List<ChannelSender> senders;
    private final UserGateway users;
    private final OutboxService outbox;

    public NotificationService(NotificationRepository notifications, List<ChannelSender> senders,
                               UserGateway users, OutboxService outbox) {
        this.notifications = notifications;
        this.senders = senders;
        this.users = users;
        this.outbox = outbox;
    }

    /** Fan-out to every channel the user can be reached on; each delivery is stored and announced as NOTIFICATION_TRIGGERED. */
    @Transactional
    public void notifyUser(UUID userId, NotificationTemplate template, Object... args) {
        UserContact contact = users.contact(userId);
        String name = contact.fullName() == null ? "there" : contact.fullName();
        Object[] full = new Object[args.length + 1];
        full[0] = name;
        System.arraycopy(args, 0, full, 1, args.length);
        String body = template.body(full);

        for (ChannelSender sender : senders) {
            if (!sender.supports(contact)) continue;
            Notification n = new Notification();
            n.setUserId(userId);
            n.setChannel(sender.channel());
            n.setTemplate(template.name());
            n.setSubject(template.subject());
            n.setBody(body);
            try {
                sender.send(contact, template.subject(), body);
                n.setStatus("SENT");
            } catch (RuntimeException e) {
                log.warn("Delivery via {} failed: {}", sender.channel(), e.getMessage());
                n.setStatus("FAILED");
                n.setError(String.valueOf(e.getMessage()));
            }
            n = notifications.save(n);
            outbox.publish(EventType.NOTIFICATION_TRIGGERED, userId,
                    new Events.NotificationTriggered(n.getId(), userId, n.getChannel().name(), template.name()));
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> forUser(UUID userId, Pageable pageable) {
        return notifications.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Notification get(UUID id) {
        return notifications.findById(id).orElseThrow(() -> new NotFoundException("Notification", id));
    }
}
