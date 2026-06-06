package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import com.minhthien.hoser_backend.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class NotificationCampaignResponse {
    private Long id;
    private String title;
    private String content;
    private NotificationAudienceType audienceType;
    private UserRole audienceRole;
    private Set<NotificationChannel> channels;
    private NotificationCampaignStatus status;
    private long recipientCount;
    private Long createdById;
    private String createdByUsername;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<NotificationChannelStatsResponse> channelStats;
}
