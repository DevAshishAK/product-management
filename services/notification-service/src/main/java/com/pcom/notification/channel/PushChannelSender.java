package com.pcom.notification.channel;

import com.pcom.common.dto.user.UserContact;
import com.pcom.notification.entity.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Integration point for push (FCM / APNs). Logs only; device tokens would live in a device registry. */
@Component
public class PushChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(PushChannelSender.class);

    @Override
    public NotificationChannel channel() { return NotificationChannel.PUSH; }

    @Override
    public boolean supports(UserContact contact) { return true; }

    @Override
    public void send(UserContact contact, String subject, String body) {
        log.info("[push:log-only] user={} title={}", contact.id(), subject);
    }
}
