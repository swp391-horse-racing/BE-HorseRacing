package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.ViolationResultAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_violations",
        indexes = {
                @Index(name = "idx_race_violations_race", columnList = "race_id"),
                @Index(name = "idx_race_violations_participant", columnList = "participant_id"),
                @Index(name = "idx_race_violations_referee", columnList = "referee_id"),
                @Index(name = "idx_race_violations_severity", columnList = "severity")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceViolation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private RaceParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jockey_id", nullable = false)
    private User jockey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_id", nullable = false)
    private User referee;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(name = "type_label", length = 100)
    private String typeLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RaceViolationSeverity severity;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "penalty_text", length = 1000)
    private String penaltyText;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_action", nullable = false, length = 40)
    @Builder.Default
    private ViolationResultAction resultAction = ViolationResultAction.NONE;

    @Column(name = "time_penalty_millis", nullable = false)
    @Builder.Default
    private Long timePenaltyMillis = 0L;

    @Column(name = "evidence_url", nullable = false, length = 1000)
    private String evidenceUrl;

    @Column(name = "evidence_name", nullable = false, length = 255)
    private String evidenceName;

    @Column(name = "evidence_type", nullable = false, length = 120)
    private String evidenceType;

    @Column(name = "evidence_size", nullable = false)
    private Long evidenceSize;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (resultAction == null) {
            resultAction = ViolationResultAction.NONE;
        }
        if (timePenaltyMillis == null) {
            timePenaltyMillis = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (resultAction == null) {
            resultAction = ViolationResultAction.NONE;
        }
        if (timePenaltyMillis == null) {
            timePenaltyMillis = 0L;
        }
    }
}
