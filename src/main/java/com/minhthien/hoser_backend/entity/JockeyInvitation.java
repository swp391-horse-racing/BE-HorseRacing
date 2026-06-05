package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "jockey_invitations",
        indexes = {
                @Index(name = "idx_jockey_invitations_owner", columnList = "owner_id"),
                @Index(name = "idx_jockey_invitations_jockey", columnList = "jockey_id"),
                @Index(name = "idx_jockey_invitations_horse", columnList = "horse_id"),
                @Index(name = "idx_jockey_invitations_race", columnList = "race_id"),
                @Index(name = "idx_jockey_invitations_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jockey_id", nullable = false)
    private User jockey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jockey_profile_id", nullable = false)
    private JockeyProfile jockeyProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.PENDING;

    @Column(length = 1000)
    private String message;

    @Column(length = 1000)
    private String responseNote;


    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal remunerationAmount = BigDecimal.ZERO;

    private LocalDateTime respondedAt;

    private LocalDateTime cancelledAt;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(length = 100)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = AssignmentStatus.PENDING;
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "SYSTEM";
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = createdBy;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = "SYSTEM";
        }
    }
}
