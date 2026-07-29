package com.minhthien.hoser_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "race_simulation_participants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_simulation_participant", columnNames = {"simulation_id", "participant_id"}),
        @UniqueConstraint(name = "uk_simulation_rank", columnNames = {"simulation_id", "result_rank"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceSimulationParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false)
    private RaceSimulation simulation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private RaceParticipant participant;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;
    @Column(name = "horse_name", nullable = false, length = 120)
    private String horseName;
    @Column(name = "jockey_id", nullable = false)
    private Long jockeyId;
    @Column(name = "jockey_name", nullable = false, length = 120)
    private String jockeyName;
    @Column(name = "gate_number", nullable = false)
    private Integer gateNumber;

    private Long horseStarts;
    private Long horseWins;
    private Double horseWinRate;
    private Long jockeyStarts;
    private Long jockeyWins;
    private Double jockeyWinRate;
    private Double historyScore;

    @Column(name = "result_rank", nullable = false)
    private Integer rank;
    @Column(name = "finish_time_millis", nullable = false)
    private Long finishTimeMillis;

    @OneToMany(mappedBy = "simulationParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("tick ASC")
    @Builder.Default
    private List<RaceSimulationCheckpoint> checkpoints = new ArrayList<>();

    public void addCheckpoint(RaceSimulationCheckpoint checkpoint) {
        checkpoint.setSimulationParticipant(this);
        checkpoints.add(checkpoint);
    }
}
