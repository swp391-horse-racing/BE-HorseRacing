package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceResultDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RaceResultDraftRepository extends JpaRepository<RaceResultDraft, Long> {
    @EntityGraph(attributePaths = {
            "race", "simulation", "rows", "rows.participant",
            "rows.participant.horse", "rows.participant.jockey"
    })
    Optional<RaceResultDraft> findByRaceId(Long raceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select draft from RaceResultDraft draft where draft.race.id = :raceId")
    Optional<RaceResultDraft> findByRaceIdForUpdate(@Param("raceId") Long raceId);
}
