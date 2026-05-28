package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.dto.response.UnreadNotificationCountResponse;
import com.minhthien.hoser_backend.entity.Notification;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationReadStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.NotificationRepository;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notificationRepository;
    private final RealtimeEventService realtimeEventService;

    @Override
    @Transactional
    public NotificationResponse notify(User recipient, NotificationType type, String title, String message,
                                       String referenceType, String referenceId, String metadataJson) {
        if (recipient == null || recipient.getId() == null || type == null
                || referenceType == null || referenceType.isBlank()
                || referenceId == null || referenceId.isBlank()) {
            return null;
        }
        try {
            Notification notification = notificationRepository
                    .findByRecipientIdAndTypeAndReferenceTypeAndReferenceId(
                            recipient.getId(), type, referenceType, referenceId)
                    .orElseGet(() -> notificationRepository.save(Notification.builder()
                            .recipient(recipient)
                            .type(type)
                            .title(title)
                            .message(message)
                            .referenceType(referenceType)
                            .referenceId(referenceId)
                            .metadataJson(metadataJson)
                            .build()));
            NotificationResponse response = map(notification);
            realtimeEventService.publishUserNotification(recipient.getId(), response);
            return response;
        } catch (DataIntegrityViolationException ex) {
            return notificationRepository.findByRecipientIdAndTypeAndReferenceTypeAndReferenceId(
                            recipient.getId(), type, referenceType, referenceId)
                    .map(this::map)
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Could not create notification: recipientId={}, type={}, referenceType={}, referenceId={}",
                    recipient.getId(), type, referenceType, referenceId, ex);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(Long userId, NotificationReadStatus status, int page, int size) {
        Pageable pageable = page(page, size);
        Page<Notification> notifications;
        if (status == NotificationReadStatus.READ) {
            notifications = notificationRepository.findByRecipientIdAndReadAtIsNotNullOrderByCreatedAtDesc(userId, pageable);
        } else if (status == NotificationReadStatus.UNREAD) {
            notifications = notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable);
        } else {
            notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        }
        return notifications.map(this::map);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount(Long userId) {
        return new UnreadNotificationCountResponse(notificationRepository.countByRecipientIdAndReadAtIsNull(userId));
    }

    @Override
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return map(notification);
    }

    @Override
    @Transactional
    public long markAllRead(Long userId) {
        Page<Notification> page;
        long updated = 0;
        do {
            page = notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
                    userId, PageRequest.of(0, 100));
            for (Notification notification : page.getContent()) {
                notification.setReadAt(LocalDateTime.now());
                updated++;
            }
            notificationRepository.saveAll(page.getContent());
        } while (page.hasNext());
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getAdminNotifications(NotificationType type, Long recipientId, int page, int size) {
        Pageable pageable = page(page, size);
        Page<Notification> notifications;
        if (recipientId != null && type != null) {
            notifications = notificationRepository.findByRecipientIdAndTypeOrderByCreatedAtDesc(
                    recipientId, type, pageable);
        } else if (recipientId != null) {
            notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
        } else if (type != null) {
            notifications = notificationRepository.findByTypeOrderByCreatedAtDesc(type, pageable);
        } else {
            notifications = notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return notifications.map(this::map);
    }

    private Pageable page(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(normalizedPage, normalizedSize);
    }

    private NotificationResponse map(Notification notification) {
        User recipient = notification.getRecipient();
        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientId(recipient.getId())
                .recipientUsername(recipient.getUsername())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .metadataJson(notification.getMetadataJson())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
