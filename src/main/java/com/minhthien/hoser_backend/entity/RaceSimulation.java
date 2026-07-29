package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceSimulationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "race_simulations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_race_simulations_race", columnNames = "race_id"),
        @UniqueConstraint(name = "uk_race_simulations_run", columnNames = "run_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceSimulation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 128)
    private String seed;

    @Column(name = "algorithm_version", nullable = false, length = 30)
    @Builder.Default
    private String algorithmVersion = "v1-time-split";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RaceSimulationStatus status;

    @Column(name = "playback_duration_ms", nullable = false)
    @Builder.Default
    private Long playbackDurationMs = 28_000L;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "playback_ends_at", nullable = false)
    private LocalDateTime playbackEndsAt;

    @Column(name = "generated_by", nullable = false)
    private Long generatedBy;

    private LocalDateTime confirmedAt;
    private Long confirmedBy;

    @Version
    private Long version;

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    @Builder.Default
    private List<RaceSimulationParticipant> participants = new ArrayList<>();

    public void addParticipant(RaceSimulationParticipant participant) {
        participant.setSimulation(this);
        participants.add(participant);
    }
}
