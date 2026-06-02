package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.Bet;
import com.minhthien.hoser_backend.enums.BetStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface BetRepository extends JpaRepository<Bet, Long> {
    @EntityGraph(attributePaths = {"market", "race", "race.tournament", "participant", "participant.horse",
            "participant.jockey", "user"})
    List<Bet> findByMarketIdOrderByPlacedAtDesc(Long marketId);

    @EntityGraph(attributePaths = {"market", "race", "race.tournament", "participant", "participant.horse",
            "participant.jockey", "user"})
    List<Bet> findByUserIdOrderByPlacedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"market", "race", "race.tournament", "participant", "participant.horse",
            "participant.jockey", "user"})
    List<Bet> findByRaceIdAndStatusIn(Long raceId, Collection<BetStatus> statuses);

    @Query("""
            select b.status, count(b)
            from Bet b
            where b.user.id = :userId
            group by b.status
            """)
    List<Object[]> countByStatusGroupForUser(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(b.stakeAmount), 0)
            from Bet b
            where b.user.id = :userId
            """)
    BigDecimal sumStakeAmountByUserId(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(b.netProfitAmount), 0)
            from Bet b
            where b.user.id = :userId
            """)
    BigDecimal sumNetProfitAmountByUserId(@Param("userId") Long userId);
}
