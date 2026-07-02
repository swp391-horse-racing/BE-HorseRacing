package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceViolation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaceViolationRepository extends JpaRepository<RaceViolation, Long> {
    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey", "referee"})
    List<RaceViolation> findByRaceIdOrderByOccurredAtDesc(Long raceId);

    @EntityGraph(attributePaths = {"race", "participant", "owner", "horse", "jockey", "referee"})
    List<RaceViolation> findByRefereeIdOrderByOccurredAtDesc(Long refereeId);

    boolean existsByRaceId(Long raceId);
}
