package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_complaints",
        indexes = {
                @Index(name = "idx_race_complaints_race", columnList = "race_id"),
                @Index(name = "idx_race_complaints_complainant", columnList = "complainant_owner_id"),
                @Index(name = "idx_race_complaints_accused", columnList = "accused_owner_id"),
                @Index(name = "idx_race_complaints_status", columnList = "status")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceComplaint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complainant_owner_id", nullable = false)
    private User complainantOwner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accused_owner_id", nullable = false)
    private User accusedOwner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accused_participant_id", nullable = false)
    private RaceParticipant accusedParticipant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RaceComplaintStatus status = RaceComplaintStatus.PENDING;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(length = 1000)
    private String evidenceUrl;

    @Column(length = 2000)
    private String adminNote;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal ownerPrizeReturnAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalPenaltyAmount = BigDecimal.ZERO;

    private LocalDateTime banUntil;

    private LocalDateTime resolvedAt;

    private Long resolvedBy;

    @Column(length = 150)
    private String ownerPrizeReturnDebitKey;

    @Column(length = 150)
    private String fineDebitKey;

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
            status = RaceComplaintStatus.PENDING;
        }
        if (ownerPrizeReturnAmount == null) {
            ownerPrizeReturnAmount = BigDecimal.ZERO;
        }
        if (fineAmount == null) {
            fineAmount = BigDecimal.ZERO;
        }
        if (totalPenaltyAmount == null) {
            totalPenaltyAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
