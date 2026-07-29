package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RaceSimulation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RaceSimulationRepository extends JpaRepository<RaceSimulation, Long> {
    boolean existsByRaceId(Long raceId);

    Optional<RaceSimulation> findByRaceId(Long raceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select simulation from RaceSimulation simulation where simulation.race.id = :raceId")
    Optional<RaceSimulation> findByRaceIdForUpdate(@Param("raceId") Long raceId);
}
