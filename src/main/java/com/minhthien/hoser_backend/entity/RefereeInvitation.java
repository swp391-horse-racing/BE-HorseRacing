package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "referee_invitations",
        indexes = {
                @Index(name = "idx_referee_invitations_admin", columnList = "admin_id"),
                @Index(name = "idx_referee_invitations_referee", columnList = "referee_id"),
                @Index(name = "idx_referee_invitations_race", columnList = "race_id"),
                @Index(name = "idx_referee_invitations_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_id", nullable = false)
    private User referee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_config_id", nullable = false)
    private RefereeSalaryConfig salaryConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.PENDING;

    @Column(length = 1000)
    private String message;

    @Column(name = "response_note", length = 1000)
    private String responseNote;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(name = "updated_by", nullable = false, length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = AssignmentStatus.PENDING;
        if (createdBy == null || createdBy.isBlank()) createdBy = "SYSTEM";
        if (updatedBy == null || updatedBy.isBlank()) updatedBy = createdBy;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updatedBy == null || updatedBy.isBlank()) updatedBy = "SYSTEM";
    }
}
