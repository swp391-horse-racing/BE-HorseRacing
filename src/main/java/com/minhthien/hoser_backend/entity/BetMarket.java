package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.BetMarketStatus;
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
        name = "bet_markets",
        indexes = {
                @Index(name = "idx_bet_markets_race", columnList = "race_id"),
                @Index(name = "idx_bet_markets_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetMarket {
    public static final BigDecimal DEFAULT_WINNING_TAX_PERCENT = new BigDecimal("10.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    private User createdByAdmin;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minStake;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal maxStake;

    @Column(name = "winning_tax_percent", nullable = false, precision = 5, scale = 2)
    @ColumnDefault("10.00")
    @Builder.Default
    private BigDecimal winningTaxPercent = DEFAULT_WINNING_TAX_PERCENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BetMarketStatus status = BetMarketStatus.DRAFT;

    @Column(length = 1000)
    private String note;

    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    private LocalDateTime settledAt;

    private LocalDateTime cancelledAt;

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
            status = BetMarketStatus.DRAFT;
        }
        if (winningTaxPercent == null) {
            winningTaxPercent = DEFAULT_WINNING_TAX_PERCENT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
