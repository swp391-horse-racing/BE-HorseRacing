package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "tournament_leaderboard_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tournament_leaderboard_result",
                        columnNames = {"tournament_id", "race_result_id"})
        },
        indexes = {
                @Index(name = "idx_tournament_leaderboard_tournament", columnList = "tournament_id"),
                @Index(name = "idx_tournament_leaderboard_race", columnList = "race_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentLeaderboardSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(nullable = false)
    private Long raceId;

    @Column(nullable = false, length = 120)
    private String raceName;

    private LocalDateTime raceScheduledStartAt;

    private LocalDateTime raceScheduledEndAt;

    @Column(nullable = false)
    private Long raceResultId;

    @Column(nullable = false)
    private Long participantId;

    private Integer raceRank;

    private Long finishTimeMillis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RaceParticipantStatus resultStatus;

    @Column(nullable = false)
    private Long horseId;

    @Column(nullable = false, length = 160)
    private String horseName;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 100)
    private String ownerUsername;

    @Column(nullable = false)
    private Long jockeyId;

    @Column(nullable = false, length = 100)
    private String jockeyUsername;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal prizeAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal ownerPrizeAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal jockeyPrizeAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal jockeyPrizePercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RacePayoutStatus payoutStatus;

    private Long resultFinalizedBy;

    private LocalDateTime resultFinalizedAt;

    private Long tournamentFinalizedBy;

    private LocalDateTime tournamentFinalizedAt;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (prizeAmount == null) {
            prizeAmount = BigDecimal.ZERO;
        }
        if (ownerPrizeAmount == null) {
            ownerPrizeAmount = BigDecimal.ZERO;
        }
        if (jockeyPrizeAmount == null) {
            jockeyPrizeAmount = BigDecimal.ZERO;
        }
        if (jockeyPrizePercent == null) {
            jockeyPrizePercent = BigDecimal.ZERO;
        }
        if (payoutStatus == null) {
            payoutStatus = RacePayoutStatus.NOT_ELIGIBLE;
        }
    }
}
