package com.minhthien.hoser_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "race_simulation_checkpoints", uniqueConstraints = {
        @UniqueConstraint(name = "uk_simulation_checkpoint_tick", columnNames = {"simulation_participant_id", "tick"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceSimulationCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_participant_id", nullable = false)
    private RaceSimulationParticipant simulationParticipant;

    @Column(nullable = false)
    private Integer tick;
    @Column(name = "at_ratio", nullable = false)
    private Double at;
    @Column(nullable = false)
    private Double progress;
}
