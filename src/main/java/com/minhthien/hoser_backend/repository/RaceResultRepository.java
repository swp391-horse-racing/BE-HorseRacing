package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, Long> {
    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey"})
    List<RaceResult> findByRaceIdOrderByRankAsc(Long raceId);

    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey"})
    List<RaceResult> findByRaceTournamentId(Long tournamentId);

    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey"})
    List<RaceResult> findByPayoutStatusOrderByFinalizedAtAscIdAsc(RacePayoutStatus payoutStatus);

    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey"})
    Optional<RaceResult> findByParticipantId(Long participantId);

    boolean existsByRaceId(Long raceId);
}
