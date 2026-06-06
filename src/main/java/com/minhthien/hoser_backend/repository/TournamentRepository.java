package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findAllByOrderByCreatedAtDesc();

    List<Tournament> findByStatusOrderByCreatedAtDesc(TournamentStatus status);

    List<Tournament> findByStatusInOrderByStartAtAsc(Collection<TournamentStatus> statuses);

    @EntityGraph(attributePaths = {"races"})
    List<Tournament> findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
            TournamentStatus status, LocalDateTime registrationOpenAt);

    @EntityGraph(attributePaths = {"races"})
    List<Tournament> findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
            TournamentStatus status, LocalDateTime registrationCloseAt);

    long countByStatus(TournamentStatus status);

    long countByStatusNot(TournamentStatus status);

    @Query("""
            select count(t)
            from Tournament t
            where t.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
              and t.createdAt >= :from
              and t.createdAt < :to
            """)
    long countActiveCreatedBetween(@Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Query("""
            select t.id, t.name, count(r), coalesce(sum(r.maxParticipants), 0)
            from Tournament t
            left join t.races r on r.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
            where t.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
            group by t.id, t.name
            """)
    List<Object[]> summarizeRegistrationCapacity();

    @Query("""
            select t.id, t.name, t.bannerUrl, t.startAt, t.status, count(r)
            from Tournament t
            left join t.races r on r.status <> com.minhthien.hoser_backend.enums.RaceStatus.CANCELLED
            where t.status <> com.minhthien.hoser_backend.enums.TournamentStatus.CANCELLED
            group by t.id, t.name, t.bannerUrl, t.startAt, t.status
            """)
    List<Object[]> summarizeFeaturedCandidates();

    @EntityGraph(attributePaths = {"jockeyChallengePrizes"})
    Optional<Tournament> findDetailById(Long id);
}
