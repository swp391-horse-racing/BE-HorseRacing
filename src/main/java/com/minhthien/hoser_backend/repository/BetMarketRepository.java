package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.BetMarket;
import com.minhthien.hoser_backend.enums.BetMarketStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BetMarketRepository extends JpaRepository<BetMarket, Long> {
    @EntityGraph(attributePaths = {"race", "race.tournament", "createdByAdmin"})
    List<BetMarket> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"race", "race.tournament", "createdByAdmin"})
    Optional<BetMarket> findByRaceIdAndStatus(Long raceId, BetMarketStatus status);

    @EntityGraph(attributePaths = {"race", "race.tournament", "createdByAdmin"})
    List<BetMarket> findByStatusOrderByRaceScheduledStartAtAsc(BetMarketStatus status);

    @EntityGraph(attributePaths = {"race", "race.tournament", "createdByAdmin"})
    List<BetMarket> findByStatusOrderByRaceScheduledStartAtAsc(BetMarketStatus status, Pageable pageable);

    long countByStatus(BetMarketStatus status);

    @EntityGraph(attributePaths = {"race", "race.tournament", "createdByAdmin"})
    Optional<BetMarket> findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(Long raceId,
                                                                         Collection<BetMarketStatus> statuses);

    boolean existsByRaceIdAndStatusIn(Long raceId, Collection<BetMarketStatus> statuses);
}
