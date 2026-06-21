package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            select rr.horse.id, rr.horse.name, rr.owner.id,
                   coalesce(rr.owner.fullName, rr.owner.username),
                   sum(case when rr.rank = 1 then 1 else 0 end),
                   coalesce(sum(rr.prizeAmount), 0)
            from RaceResult rr
            where rr.race.resultFinalizedAt is not null
            group by rr.horse.id, rr.horse.name, rr.owner.id,
                     rr.owner.fullName, rr.owner.username
            order by sum(case when rr.rank = 1 then 1 else 0 end) desc,
                     coalesce(sum(rr.prizeAmount), 0) desc,
                     rr.horse.id asc
            """)
    List<Object[]> findTopHorseStatistics(Pageable pageable);

    @Query("""
            select rr.horse.id, rr.horse.name, rr.owner.id,
                   coalesce(rr.owner.fullName, rr.owner.username),
                   sum(case when rr.rank = 1 then 1 else 0 end),
                   sum(case when rr.rank in (1, 2, 3) then 1 else 0 end),
                   count(rr),
                   coalesce(sum(rr.prizeAmount), 0)
            from RaceResult rr
            where rr.race.resultFinalizedAt is not null
            group by rr.horse.id, rr.horse.name, rr.owner.id,
                     rr.owner.fullName, rr.owner.username
            order by sum(case when rr.rank = 1 then 1 else 0 end) desc,
                     sum(case when rr.rank in (1, 2, 3) then 1 else 0 end) desc,
                     coalesce(sum(rr.prizeAmount), 0) desc,
                     count(rr) desc,
                     rr.horse.id asc
            """)
    List<Object[]> findHorseRankingStatistics(Pageable pageable);

    @Query("""
            select rr.jockey.id, rr.jockey.username,
                   coalesce(rr.jockey.fullName, rr.jockey.username),
                   sum(case when rr.rank = 1 then 1 else 0 end),
                   sum(case when rr.rank in (1, 2, 3) then 1 else 0 end),
                   count(rr),
                   coalesce(sum(rr.jockeyPrizeAmount), 0)
            from RaceResult rr
            where rr.race.resultFinalizedAt is not null
            group by rr.jockey.id, rr.jockey.username,
                     rr.jockey.fullName
            order by sum(case when rr.rank = 1 then 1 else 0 end) desc,
                     sum(case when rr.rank in (1, 2, 3) then 1 else 0 end) desc,
                     coalesce(sum(rr.jockeyPrizeAmount), 0) desc,
                     count(rr) desc,
                     rr.jockey.id asc
            """)
    List<Object[]> findJockeyRankingStatistics(Pageable pageable);

    @Query("""
            select coalesce(sum(rr.prizeAmount), 0)
            from RaceResult rr
            where rr.race.resultFinalizedAt is not null
            """)
    java.math.BigDecimal sumFinalizedPrizeAmount();
}
