package com.pcom.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.user.UserContact;
import com.pcom.common.persistence.OutboxService;
import com.pcom.notification.channel.ChannelSender;
import com.pcom.notification.client.UserGateway;
import com.pcom.notification.entity.Notification;
import com.pcom.notification.entity.NotificationChannel;
import com.pcom.notification.repository.NotificationRepository;
import com.pcom.notification.service.NotificationService;
import com.pcom.notification.service.NotificationTemplate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationServiceTest {
    NotificationRepository repo = Mockito.mock(NotificationRepository.class);
    UserGateway users = Mockito.mock(UserGateway.class);
    OutboxService outbox = Mockito.mock(OutboxService.class);
    ChannelSender email = Mockito.mock(ChannelSender.class);
    ChannelSender sms = Mockito.mock(ChannelSender.class);
    NotificationService service = new NotificationService(repo, List.of(email, sms), users, outbox);
    UUID userId = UUID.randomUUID();

    @Test
    void fansOutToSupportedChannelsAndRecordsEachDelivery() {
        UserContact contact = new UserContact(userId, "a@b.co", null, "Jane");
        when(users.contact(userId)).thenReturn(contact);
        when(email.supports(contact)).thenReturn(true);
        when(email.channel()).thenReturn(NotificationChannel.EMAIL);
        when(sms.supports(contact)).thenReturn(false);
        when(repo.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
            return n;
        });
        UUID orderId = UUID.randomUUID();
        service.notifyUser(userId, NotificationTemplate.ORDER_CONFIRMED, orderId);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getBody()).contains("Jane").contains(orderId.toString());
        assertThat(captor.getValue().getStatus()).isEqualTo("SENT");
        verify(outbox).publish(eq(EventType.NOTIFICATION_TRIGGERED), eq(userId), any());
    }

    @Test
    void failedDeliveryIsRecordedNotThrown() {
        UserContact contact = new UserContact(userId, "a@b.co", null, null);
        when(users.contact(userId)).thenReturn(contact);
        when(email.supports(contact)).thenReturn(true);
        when(email.channel()).thenReturn(NotificationChannel.EMAIL);
        doThrow(new RuntimeException("smtp down")).when(email).send(any(), any(), any());
        when(repo.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
            return n;
        });
        service.notifyUser(userId, NotificationTemplate.PAYMENT_COMPLETED, UUID.randomUUID());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void templatesTolerateFewerArguments() {
        assertThat(NotificationTemplate.ORDER_CONFIRMED.body("Jane", "o-1", "unused")).contains("Jane").contains("o-1");
    }
}
