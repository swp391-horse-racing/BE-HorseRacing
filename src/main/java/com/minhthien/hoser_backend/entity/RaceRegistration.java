package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_registrations",
        indexes = {
                @Index(name = "idx_race_registrations_race", columnList = "race_id"),
                @Index(name = "idx_race_registrations_owner", columnList = "owner_id"),
                @Index(name = "idx_race_registrations_horse", columnList = "horse_id"),
                @Index(name = "idx_race_registrations_jockey", columnList = "jockey_id"),
                @Index(name = "idx_race_registrations_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

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
    @JoinColumn(name = "jockey_invitation_id", nullable = false)
    private JockeyInvitation jockeyInvitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RaceRegistrationStatus status = RaceRegistrationStatus.PENDING;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal entryFeeAmount = BigDecimal.ZERO;

    @Column(length = 150)
    private String entryFeeDebitKey;

    @Column(length = 150)
    private String entryFeeRefundKey;

    @Column(length = 1000)
    private String ownerNote;

    @Column(length = 1000)
    private String reviewNote;

    @Column(length = 1000)
    private String withdrawNote;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

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
        if (status == null) {
            status = RaceRegistrationStatus.PENDING;
        }
        if (entryFeeAmount == null) {
            entryFeeAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
