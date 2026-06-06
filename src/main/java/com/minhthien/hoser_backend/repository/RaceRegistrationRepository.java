package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface RaceRegistrationRepository extends JpaRepository<RaceRegistration, Long> {
    @EntityGraph(attributePaths = {"race", "race.tournament", "owner", "horse", "jockey", "jockeyInvitation"})
    List<RaceRegistration> findByRaceIdOrderByCreatedAtDesc(Long raceId);

    @EntityGraph(attributePaths = {"race", "race.tournament", "owner", "horse", "jockey", "jockeyInvitation"})
    List<RaceRegistration> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"race", "race.tournament", "race.referee", "owner", "horse",
            "jockey", "jockeyInvitation"})
    List<RaceRegistration> findByJockeyIdOrderByCreatedAtDesc(Long jockeyId);

    @EntityGraph(attributePaths = {"race", "race.tournament", "owner", "horse", "jockey", "jockeyInvitation"})
    List<RaceRegistration> findByRaceTournamentIdOrderByCreatedAtDesc(Long tournamentId);

    boolean existsByRaceIdAndHorseIdAndStatusIn(Long raceId, Long horseId, Collection<RaceRegistrationStatus> statuses);

    boolean existsByRaceId(Long raceId);

    boolean existsByRaceTournamentId(Long tournamentId);

    boolean existsByHorseId(Long horseId);

    @Query("""
            select count(rr)
            from RaceRegistration rr
            where rr.status in :statuses
              and rr.race.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
              and rr.race.tournament.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
            """)
    long countValidForAdminDashboard(@Param("statuses") Collection<RaceRegistrationStatus> statuses);

    @Query("""
            select rr.race.tournament.id, rr.race.tournament.name, count(rr)
            from RaceRegistration rr
            where rr.status in :statuses
              and rr.race.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
              and rr.race.tournament.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
            group by rr.race.tournament.id, rr.race.tournament.name
            """)
    List<Object[]> countValidByTournament(@Param("statuses") Collection<RaceRegistrationStatus> statuses);

    @Query("""
            select year(rr.createdAt), month(rr.createdAt), count(rr)
            from RaceRegistration rr
            where rr.status in :statuses
              and rr.race.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
              and rr.race.tournament.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
              and rr.createdAt >= :from
              and rr.createdAt < :to
            group by year(rr.createdAt), month(rr.createdAt)
            order by year(rr.createdAt), month(rr.createdAt)
            """)
    List<Object[]> countValidByMonth(@Param("statuses") Collection<RaceRegistrationStatus> statuses,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    @Query("""
            select count(rr) > 0
            from RaceRegistration rr
            where rr.horse.id = :horseId
              and rr.status in :statuses
              and rr.race.scheduledStartAt > :windowStart
              and rr.race.scheduledStartAt < :windowEnd
            """)
    boolean existsActiveHorseRegistrationWithinWindow(@Param("horseId") Long horseId,
                                                      @Param("statuses") Collection<RaceRegistrationStatus> statuses,
                                                      @Param("windowStart") LocalDateTime windowStart,
                                                      @Param("windowEnd") LocalDateTime windowEnd);

    @Query("""
            select count(rr) > 0
            from RaceRegistration rr
            where rr.jockey.id = :jockeyId
              and rr.status in :statuses
              and rr.race.scheduledStartAt < :scheduledEndAt
              and rr.race.scheduledEndAt > :scheduledStartAt
            """)
    boolean existsActiveJockeyOverlap(@Param("jockeyId") Long jockeyId,
                                      @Param("statuses") Collection<RaceRegistrationStatus> statuses,
                                      @Param("scheduledStartAt") LocalDateTime scheduledStartAt,
                                      @Param("scheduledEndAt") LocalDateTime scheduledEndAt);
}
