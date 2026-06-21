package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RefereePaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "referee_race_payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_referee_race_payments_race", columnNames = "race_id")
        },
        indexes = {
                @Index(name = "idx_referee_race_payments_referee", columnList = "referee_id"),
                @Index(name = "idx_referee_race_payments_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeRacePayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false, unique = true)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_id", nullable = false)
    private User referee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salary_config_id", nullable = false)
    private RefereeSalaryConfig salaryConfig;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefereePaymentStatus status;

    @Column(name = "hold_idempotency_key", nullable = false, length = 150)
    private String holdIdempotencyKey;

    @Column(name = "capture_idempotency_key", nullable = false, length = 150)
    private String captureIdempotencyKey;

    @Column(name = "credit_idempotency_key", nullable = false, length = 150)
    private String creditIdempotencyKey;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
