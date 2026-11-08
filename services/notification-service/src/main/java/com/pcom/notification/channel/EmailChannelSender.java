package com.pcom.notification.channel;

import com.pcom.common.dto.user.UserContact;
import com.pcom.notification.entity.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends through SMTP when spring.mail.host is configured, otherwise logs (local development). */
@Component
public class EmailChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public EmailChannelSender(ObjectProvider<JavaMailSender> mailSender,
                              @Value("${app.notification.from:no-reply@pcom.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public NotificationChannel channel() { return NotificationChannel.EMAIL; }

    @Override
    public boolean supports(UserContact contact) { return contact.email() != null; }

    @Override
    public void send(UserContact contact, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[email:log-only] to={} subject={}", contact.email(), subject);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(contact.email());
        msg.setSubject(subject);
        msg.setText(body);
        sender.send(msg);
    }
}
