package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RefereeRacePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefereeRacePaymentRepository extends JpaRepository<RefereeRacePayment, Long> {
    @EntityGraph(attributePaths = {"race", "race.tournament", "referee", "salaryConfig"})
    Optional<RefereeRacePayment> findByRaceId(Long raceId);

    @EntityGraph(attributePaths = {"race", "race.tournament", "referee", "salaryConfig"})
    List<RefereeRacePayment> findByRefereeIdOrderByCreatedAtDesc(Long refereeId);

    boolean existsBySalaryConfigId(Long salaryConfigId);
}
