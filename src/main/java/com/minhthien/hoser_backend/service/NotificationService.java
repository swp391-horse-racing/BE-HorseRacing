package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.dto.response.UnreadNotificationCountResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationReadStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import org.springframework.data.domain.Page;

public interface NotificationService {
    NotificationResponse notify(User recipient, NotificationType type, String title, String message,
                                String referenceType, String referenceId, String metadataJson);

    Page<NotificationResponse> getMyNotifications(Long userId, NotificationReadStatus status, int page, int size);

    UnreadNotificationCountResponse getUnreadCount(Long userId);

    NotificationResponse markRead(Long userId, Long notificationId);

    long markAllRead(Long userId);

    Page<NotificationResponse> getAdminNotifications(NotificationType type, Long recipientId, int page, int size);
}
