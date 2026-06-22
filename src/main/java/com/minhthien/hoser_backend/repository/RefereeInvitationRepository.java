package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.RefereeInvitation;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefereeInvitationRepository extends JpaRepository<RefereeInvitation, Long> {
    @EntityGraph(attributePaths = {"admin", "referee", "race", "race.tournament", "race.venue", "salaryConfig"})
    List<RefereeInvitation> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"admin", "referee", "race", "race.tournament", "race.venue", "salaryConfig"})
    List<RefereeInvitation> findByRefereeIdOrderByCreatedAtDesc(Long refereeId);

    @EntityGraph(attributePaths = {"admin", "referee", "race", "race.tournament", "race.venue", "salaryConfig"})
    @Query("select invitation from RefereeInvitation invitation where invitation.id = :id")
    Optional<RefereeInvitation> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from RefereeInvitation invitation where invitation.id = :id")
    Optional<RefereeInvitation> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRaceIdAndRefereeIdAndStatus(Long raceId, Long refereeId, AssignmentStatus status);

    List<RefereeInvitation> findByRaceIdAndStatusAndIdNotOrderByCreatedAtDesc(
            Long raceId, AssignmentStatus status, Long id);

    List<RefereeInvitation> findByRaceIdAndStatusOrderByCreatedAtDesc(Long raceId, AssignmentStatus status);
}
