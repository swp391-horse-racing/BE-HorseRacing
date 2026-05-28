package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private String recipientUsername;
    private NotificationType type;
    private String title;
    private String message;
    private String referenceType;
    private String referenceId;
    private String metadataJson;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
