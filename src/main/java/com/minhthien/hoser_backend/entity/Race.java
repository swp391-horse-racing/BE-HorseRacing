package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "races",
        indexes = {
                @Index(name = "idx_races_tournament", columnList = "tournament_id"),
                @Index(name = "idx_races_schedule", columnList = "scheduled_start_at, scheduled_end_at"),
                @Index(name = "idx_races_referee", columnList = "referee_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Race {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String distance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_track_id")
    private RaceTrack raceTrack;

    @Column(name = "scheduled_start_at", nullable = false)
    private LocalDateTime scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private LocalDateTime scheduledEndAt;

    @Column(nullable = false)
    private Integer minParticipants;

    @Column(nullable = false)
    private Integer maxParticipants;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal entryFee = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referee_id")
    private User referee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RaceStatus status = RaceStatus.DRAFT;

    @Column(length = 1000)
    private String note;

    private LocalDateTime resultFinalizedAt;

    private Long resultFinalizedBy;

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    @Builder.Default
    private List<RacePrize> prizes = new ArrayList<>();

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("gateNumber ASC")
    @Builder.Default
    private List<RaceParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RaceRegistration> registrations = new ArrayList<>();

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
        if (entryFee == null) {
            entryFee = BigDecimal.ZERO;
        }
        if (status == null) {
            status = RaceStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void replacePrizes(List<RacePrize> newPrizes) {
        prizes.clear();
        if (newPrizes != null) {
            newPrizes.forEach(prize -> {
                prize.setRace(this);
                prizes.add(prize);
            });
        }
    }
}
