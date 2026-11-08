package com.pcom.notification.channel;

import com.pcom.common.dto.user.UserContact;
import com.pcom.notification.entity.NotificationChannel;

public interface ChannelSender {
    NotificationChannel channel();

    boolean supports(UserContact contact);

    void send(UserContact contact, String subject, String body);
}
