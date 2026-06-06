package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.enums.RaceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface RaceRepository extends JpaRepository<Race, Long> {
    @EntityGraph(attributePaths = {"tournament", "referee", "prizes"})
    List<Race> findByTournamentIdOrderByScheduledStartAtAsc(Long tournamentId);

    List<Race> findByRefereeIdOrderByScheduledStartAtAsc(Long refereeId);

    @EntityGraph(attributePaths = {"tournament", "referee"})
    List<Race> findByRefereeIdOrderByScheduledStartAtAsc(Long refereeId, Pageable pageable);

    List<Race> findByTournamentIdAndStatusIn(Long tournamentId, Collection<RaceStatus> statuses);

    long countByStatus(RaceStatus status);

    long countByStatusNot(RaceStatus status);

    @Query("""
            select count(r)
            from Race r
            where r.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
              and r.tournament.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
            """)
    long countActiveForAdminDashboard();

    @Query("""
            select count(r)
            from Race r
            where r.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
              and r.tournament.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
              and r.createdAt >= :from
              and r.createdAt < :to
            """)
    long countActiveCreatedBetween(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    long countByScheduledStartAtBetween(LocalDateTime startAt, LocalDateTime endAt);

    @EntityGraph(attributePaths = {"tournament", "referee"})
    List<Race> findByScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc(LocalDateTime startAt, Pageable pageable);

    @EntityGraph(attributePaths = {"tournament", "referee"})
    List<Race> findByScheduledStartAtBetweenOrderByScheduledStartAtAsc(
            LocalDateTime startAt,
            LocalDateTime endAt,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tournament", "referee"})
    List<Race> findByStatusAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
            RaceStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tournament", "referee", "participants", "participants.owner",
            "participants.horse", "participants.jockey", "participants.registration"})
    List<Race> findByStatusAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
            RaceStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    boolean existsByRefereeIdAndScheduledStartAtLessThanAndScheduledEndAtGreaterThan(
            Long refereeId,
            LocalDateTime scheduledEndAt,
            LocalDateTime scheduledStartAt
    );

    @Query("""
            select count(r) > 0
            from Race r
            where r.referee.id = :refereeId
              and r.id <> :raceId
              and r.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
              and r.scheduledStartAt < :scheduledEndAt
              and r.scheduledEndAt > :scheduledStartAt
            """)
    boolean existsRefereeOverlapExcludingRace(@Param("refereeId") Long refereeId,
                                              @Param("raceId") Long raceId,
                                              @Param("scheduledStartAt") LocalDateTime scheduledStartAt,
                                              @Param("scheduledEndAt") LocalDateTime scheduledEndAt);
}
