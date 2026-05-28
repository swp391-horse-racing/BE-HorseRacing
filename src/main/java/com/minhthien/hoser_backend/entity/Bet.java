package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.BetStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "bets",
        indexes = {
                @Index(name = "idx_bets_market", columnList = "market_id"),
                @Index(name = "idx_bets_race_status", columnList = "race_id, status"),
                @Index(name = "idx_bets_user", columnList = "user_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id", nullable = false)
    private BetMarket market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private RaceParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal stakeAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal potentialPayoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BetStatus status = BetStatus.PLACED;

    @Column(length = 150)
    private String stakeHoldKey;

    @Column(length = 150)
    private String stakeCaptureKey;

    @Column(length = 150)
    private String adminStakeCreditKey;

    @Column(length = 150)
    private String stakeReleaseKey;

    @Column(length = 150)
    private String profitAdminDebitKey;

    @Column(length = 150)
    private String profitCreditKey;

    @Column(precision = 5, scale = 2)
    private BigDecimal winningTaxPercent;

    @Column(precision = 19, scale = 2)
    private BigDecimal winningTaxAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal grossProfitAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal netProfitAmount;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime placedAt = LocalDateTime.now();

    private LocalDateTime lockedAt;

    private LocalDateTime settledAt;

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
        if (placedAt == null) {
            placedAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = BetStatus.PLACED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
