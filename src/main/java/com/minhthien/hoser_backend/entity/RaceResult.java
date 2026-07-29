package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import com.minhthien.hoser_backend.enums.RaceResultSource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_results",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_race_result_participant", columnNames = {"race_id", "participant_id"}),
                @UniqueConstraint(name = "uk_race_result_rank", columnNames = {"race_id", "result_rank"})
        },
        indexes = {
                @Index(name = "idx_race_results_race", columnList = "race_id"),
                @Index(name = "idx_race_results_jockey", columnList = "jockey_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceResult {
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

    @Column(name = "result_rank")
    private Integer rank;

    private Long finishTimeMillis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @ColumnDefault("'MANUAL'")
    @Builder.Default
    private RaceResultSource source = RaceResultSource.MANUAL;

    @Column(name = "simulation_run_id", length = 64)
    private String simulationRunId;

    @Column(name = "base_finish_time_millis")
    private Long baseFinishTimeMillis;

    @Column(name = "penalty_time_millis", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Long penaltyTimeMillis = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RaceParticipantStatus status;

    private Integer jockeyChallengePoints;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal prizeAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal ownerPrizeAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal jockeyPrizeAmount = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal jockeyPrizePercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RacePayoutStatus payoutStatus = RacePayoutStatus.NOT_ELIGIBLE;

    @Column(length = 1000)
    private String note;

    private Long finalizedBy;

    private LocalDateTime finalizedAt;

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
        if (source == null) {
            source = RaceResultSource.MANUAL;
        }
        if (penaltyTimeMillis == null) {
            penaltyTimeMillis = 0L;
        }
    }
}
