package com.minhthien.hoser_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_reminder_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_race_reminder_event_recipient",
                        columnNames = {"race_id", "recipient_id", "event_type"})
        },
        indexes = {
                @Index(name = "idx_race_reminder_race", columnList = "race_id"),
                @Index(name = "idx_race_reminder_recipient", columnList = "recipient_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceReminderLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
