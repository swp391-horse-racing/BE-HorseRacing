package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.TournamentLeaderboardSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentLeaderboardSnapshotRepository extends JpaRepository<TournamentLeaderboardSnapshot, Long> {
    @EntityGraph(attributePaths = {"tournament"})
    List<TournamentLeaderboardSnapshot> findByTournamentIdOrderByRaceScheduledStartAtAscRaceRankAscIdAsc(Long tournamentId);

    boolean existsByTournamentId(Long tournamentId);
}
