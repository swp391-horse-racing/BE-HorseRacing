package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RaceParticipantRepository extends JpaRepository<RaceParticipant, Long> {
    @EntityGraph(attributePaths = {"race", "owner", "horse", "jockey", "registration"})
    List<RaceParticipant> findByRaceIdOrderByGateNumberAsc(Long raceId);

    @EntityGraph(attributePaths = {"race", "owner", "horse", "jockey", "registration"})
    List<RaceParticipant> findByRaceTournamentId(Long tournamentId);

    Optional<RaceParticipant> findByRegistrationId(Long registrationId);

    boolean existsByRaceIdAndGateNumber(Long raceId, Integer gateNumber);

    boolean existsByRaceIdAndGateNumberAndIdNot(Long raceId, Integer gateNumber, Long id);

    boolean existsByRaceId(Long raceId);

    boolean existsByRaceTournamentId(Long tournamentId);

    boolean existsByHorseId(Long horseId);

    long countByRaceTournamentId(Long tournamentId);

    long countByRaceRefereeIdAndStatus(Long refereeId, RaceParticipantStatus status);

    @Query("""
            select rp.race.id, count(rp)
            from RaceParticipant rp
            where rp.race.id in :raceIds
            group by rp.race.id
            """)
    List<Object[]> countByRaceIds(@Param("raceIds") Collection<Long> raceIds);
}
