package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.Bet;
import com.minhthien.hoser_backend.enums.BetStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
