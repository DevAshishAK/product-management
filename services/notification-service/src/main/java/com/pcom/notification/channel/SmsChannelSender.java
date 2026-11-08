package com.pcom.notification.channel;

import com.pcom.common.dto.user.UserContact;
import com.pcom.notification.entity.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Integration point for an SMS provider (Twilio, AWS SNS, MSG91...). Logs only. */
@Component
public class SmsChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(SmsChannelSender.class);

    @Override
    public NotificationChannel channel() { return NotificationChannel.SMS; }

    @Override
    public boolean supports(UserContact contact) { return contact.phone() != null && !contact.phone().isBlank(); }

    @Override
    public void send(UserContact contact, String subject, String body) {
        log.info("[sms:log-only] to={} text={}", contact.phone(), subject);
    }
}
