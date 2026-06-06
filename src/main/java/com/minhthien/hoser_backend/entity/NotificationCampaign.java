package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import com.minhthien.hoser_backend.enums.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "notification_campaigns",
        indexes = {
                @Index(name = "idx_notification_campaign_status_schedule",
                        columnList = "status, scheduled_at"),
                @Index(name = "idx_notification_campaign_created",
                        columnList = "created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCampaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private NotificationAudienceType audienceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_role", length = 20)
    private UserRole audienceRole;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "notification_campaign_channels",
            joinColumns = @JoinColumn(name = "campaign_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_notification_campaign_channel",
                    columnNames = {"campaign_id", "channel"})
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @Builder.Default
    private Set<NotificationChannel> channels = new LinkedHashSet<>();

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private NotificationCampaignStatus status = NotificationCampaignStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "recipient_count", nullable = false)
    @Builder.Default
    private long recipientCount = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = NotificationCampaignStatus.SCHEDULED;
        }
        if (channels == null) {
            channels = new LinkedHashSet<>();
        }
    }
}
