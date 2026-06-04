package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    List<RaceResult> findByJockeyId(Long jockeyId);

    @EntityGraph(attributePaths = {"race", "race.tournament", "participant", "owner", "horse", "jockey"})
    List<RaceResult> findByJockeyIdOrderByRaceScheduledStartAtDesc(Long jockeyId);

    @EntityGraph(attributePaths = {"race", "race.tournament", "participant", "owner", "horse", "jockey"})
    List<RaceResult> findByHorseIdOrderByRaceScheduledStartAtDesc(Long horseId);

    long countByJockeyId(Long jockeyId);

    @Query("""
            select count(rr)
            from RaceResult rr
            where rr.jockey.id = :jockeyId
              and rr.race.resultFinalizedAt is not null
            """)
    long countCompletedByJockeyId(@Param("jockeyId") Long jockeyId);

    long countByJockeyIdAndRank(Long jockeyId, Integer rank);

    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey"})
    Optional<RaceResult> findByParticipantId(Long participantId);

    boolean existsByRaceId(Long raceId);

    boolean existsByRaceTournamentId(Long tournamentId);

    boolean existsByHorseId(Long horseId);
}
