package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RaceResultDraftStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "race_result_drafts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_race_result_drafts_race", columnNames = "race_id"),
        @UniqueConstraint(name = "uk_race_result_drafts_simulation", columnNames = "simulation_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaceResultDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false)
    private RaceSimulation simulation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RaceResultDraftStatus status;

    @Column(name = "draft_version", nullable = false)
    @Builder.Default
    private Long draftVersion = 1L;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    @Column(nullable = false)
    private Long updatedBy;
    private LocalDateTime publishedAt;
    private Long publishedBy;

    @Version
    private Long entityVersion;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    @Builder.Default
    private List<RaceResultDraftRow> rows = new ArrayList<>();

    public void addRow(RaceResultDraftRow row) {
        row.setDraft(this);
        rows.add(row);
    }
}
