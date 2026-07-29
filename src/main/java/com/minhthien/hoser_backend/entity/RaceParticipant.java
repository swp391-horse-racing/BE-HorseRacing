package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "race_participants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_race_participant_horse", columnNames = {"race_id", "horse_id"}),
                @UniqueConstraint(name = "uk_race_participant_gate", columnNames = {"race_id", "gate_number"})
        },
        indexes = {
                @Index(name = "idx_race_participants_race", columnList = "race_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, unique = true)
    private RaceRegistration registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jockey_id", nullable = false)
    private User jockey;

    @Column(name = "gate_number")
    private Integer gateNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RaceParticipantStatus status = RaceParticipantStatus.REGISTERED;

    @Column(length = 1000)
    private String checkInNote;

    private LocalDateTime checkedInAt;

    private Long checkedInBy;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = RaceParticipantStatus.REGISTERED;
        }
    }
}
