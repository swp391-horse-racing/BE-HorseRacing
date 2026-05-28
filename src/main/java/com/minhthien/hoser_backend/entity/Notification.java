package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_recipient_type_reference",
                        columnNames = {"recipient_id", "type", "reference_type", "reference_id"})
        },
        indexes = {
                @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at"),
                @Index(name = "idx_notifications_type", columnList = "type"),
                @Index(name = "idx_notifications_reference", columnList = "reference_type, reference_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "reference_type", nullable = false, length = 80)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 80)
    private String referenceId;

    @Lob
    private String metadataJson;

    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
