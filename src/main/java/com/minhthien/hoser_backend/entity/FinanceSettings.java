package com.minhthien.hoser_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSettings {
    public static final Long SINGLETON_ID = 1L;
    public static final BigDecimal DEFAULT_BET_WINNING_TAX_PERCENT = new BigDecimal("0.00");
    public static final boolean DEFAULT_BETTING_ENABLED = true;

    @Id
    private Long id;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal betWinningTaxPercent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean bettingEnabled = DEFAULT_BETTING_ENABLED;

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
        if (id == null) {
            id = SINGLETON_ID;
        }
        if (betWinningTaxPercent == null) {
            betWinningTaxPercent = DEFAULT_BET_WINNING_TAX_PERCENT;
        }
        if (bettingEnabled == null) {
            bettingEnabled = DEFAULT_BETTING_ENABLED;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
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
