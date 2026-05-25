package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceParticipant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RaceParticipantRepository extends JpaRepository<RaceParticipant, Long> {
    @EntityGraph(attributePaths = {"race", "owner", "horse", "jockey", "registration"})
    List<RaceParticipant> findByRaceIdOrderByGateNumberAsc(Long raceId);

    Optional<RaceParticipant> findByRegistrationId(Long registrationId);

    boolean existsByRaceIdAndGateNumber(Long raceId, Integer gateNumber);

    boolean existsByRaceIdAndGateNumberAndIdNot(Long raceId, Integer gateNumber, Long id);

    long countByRaceTournamentId(Long tournamentId);
}
