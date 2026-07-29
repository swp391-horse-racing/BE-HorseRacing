package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "race_result_draft_rows", uniqueConstraints = {
        @UniqueConstraint(name = "uk_result_draft_participant", columnNames = {"draft_id", "participant_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceResultDraftRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private RaceResultDraft draft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private RaceParticipant participant;

    @Column(name = "base_rank", nullable = false)
    private Integer baseRank;
    @Column(name = "result_rank")
    private Integer rank;
    @Column(name = "base_finish_time_millis", nullable = false)
    private Long baseFinishTimeMillis;
    @Column(name = "penalty_time_millis", nullable = false)
    @Builder.Default
    private Long penaltyTimeMillis = 0L;
    private Long finishTimeMillis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RaceParticipantStatus status;

    @Column(name = "disqualification_reason", length = 1000)
    private String disqualificationReason;
}
