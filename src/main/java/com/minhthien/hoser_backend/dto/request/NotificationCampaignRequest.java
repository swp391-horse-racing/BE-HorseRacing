package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import com.minhthien.hoser_backend.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class NotificationCampaignRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @Size(max = 1000, message = "Content must not exceed 1000 characters")
    private String content;

    @NotNull(message = "Audience type is required")
    private NotificationAudienceType audienceType;

    private UserRole audienceRole;

    @NotEmpty(message = "At least one channel is required")
    private Set<NotificationChannel> channels;

    private LocalDateTime scheduledAt;
}
