package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.entity.Notification;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationReadStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.NotificationRepository;
import com.minhthien.hoser_backend.service.RealtimeEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {
    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private RealtimeEventService realtimeEventService;

    @Test
    void notifyCreatesNotificationAndPublishesUserEvent() {
        NotificationServiceImpl service = service();
        User recipient = user(10L);
        when(notificationRepository.findByRecipientIdAndTypeAndReferenceTypeAndReferenceId(
                10L, NotificationType.REGISTRATION_CREATED, "RACE_REGISTRATION", "99"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(1L);
            return notification;
        });

        NotificationResponse response = service.notify(recipient, NotificationType.REGISTRATION_CREATED,
                "Submitted", "Registration submitted", "RACE_REGISTRATION", "99",
                "{\"raceId\":20}");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMetadataJson()).contains("raceId");
        verify(realtimeEventService).publishUserNotification(10L, response);
    }

    @Test
    void notifyDoesNotCreateDuplicateForSameRecipientTypeAndReference() {
        NotificationServiceImpl service = service();
        Notification existing = notification(2L, user(10L), NotificationType.RACE_SCHEDULED,
                "RACE", "20");
        when(notificationRepository.findByRecipientIdAndTypeAndReferenceTypeAndReferenceId(
                10L, NotificationType.RACE_SCHEDULED, "RACE", "20"))
                .thenReturn(Optional.of(existing));

        NotificationResponse response = service.notify(existing.getRecipient(), NotificationType.RACE_SCHEDULED,
                "Race scheduled", "Race scheduled", "RACE", "20", null);

        assertThat(response.getId()).isEqualTo(2L);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void userReadOperationsOnlyUseUserScopedQueries() {
        NotificationServiceImpl service = service();
        Notification unread = notification(3L, user(10L), NotificationType.DEPOSIT_PAID,
                "DEPOSIT_ORDER", "DEP-1");
        when(notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
                org.mockito.Mockito.eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unread)));
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(10L)).thenReturn(1L);
        when(notificationRepository.findByIdAndRecipientId(3L, 10L)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Page<NotificationResponse> page = service.getMyNotifications(10L, NotificationReadStatus.UNREAD, 0, 20);
        NotificationResponse read = service.markRead(10L, 3L);

        assertThat(page.getContent()).hasSize(1);
        assertThat(service.getUnreadCount(10L).getCount()).isEqualTo(1);
        assertThat(read.getReadAt()).isNotNull();
        verify(notificationRepository).findByIdAndRecipientId(3L, 10L);
    }

    @Test
    void adminCanListAllNotificationsWithFilters() {
        NotificationServiceImpl service = service();
        Notification notification = notification(4L, user(10L), NotificationType.WITHDRAWAL_CREATED,
                "USER_WITHDRAWAL", "8");
        when(notificationRepository.findByRecipientIdAndTypeOrderByCreatedAtDesc(
                org.mockito.Mockito.eq(10L), org.mockito.Mockito.eq(NotificationType.WITHDRAWAL_CREATED),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        Page<NotificationResponse> page = service.getAdminNotifications(
                NotificationType.WITHDRAWAL_CREATED, 10L, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getType()).isEqualTo(NotificationType.WITHDRAWAL_CREATED);
    }

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(notificationRepository, realtimeEventService);
    }

    private Notification notification(Long id, User recipient, NotificationType type,
                                      String referenceType, String referenceId) {
        return Notification.builder()
                .id(id)
                .recipient(recipient)
                .type(type)
                .title("Title")
                .message("Message")
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .username("user-" + id)
                .email("user-" + id + "@example.com")
                .role(UserRole.USER)
                .active(true)
                .build();
    }
}
