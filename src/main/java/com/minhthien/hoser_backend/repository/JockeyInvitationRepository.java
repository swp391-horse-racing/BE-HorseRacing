package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface JockeyInvitationRepository extends JpaRepository<JockeyInvitation, Long> {
    @EntityGraph(attributePaths = {"owner", "jockey", "horse", "race", "race.tournament", "jockeyProfile", "jockeyProfile.user"})
    List<JockeyInvitation> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = {"owner", "jockey", "horse", "race", "race.tournament", "jockeyProfile", "jockeyProfile.user"})
    List<JockeyInvitation> findByJockeyIdOrderByCreatedAtDesc(Long jockeyId);

    @EntityGraph(attributePaths = {"owner", "jockey", "horse", "race", "race.tournament", "jockeyProfile", "jockeyProfile.user"})
    List<JockeyInvitation> findByJockeyIdAndStatusAndIdNotOrderByCreatedAtDesc(
            Long jockeyId,
            AssignmentStatus status,
            Long id
    );

    @EntityGraph(attributePaths = {"owner", "jockey", "horse", "horse.owner", "race", "race.tournament", "jockeyProfile", "jockeyProfile.user"})
    List<JockeyInvitation> findByOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, AssignmentStatus status);

    @EntityGraph(attributePaths = {"owner", "jockey", "horse", "horse.owner", "race", "race.tournament", "jockeyProfile", "jockeyProfile.user"})
    List<JockeyInvitation> findByStatusOrderByCreatedAtDesc(AssignmentStatus status);

    boolean existsByHorseIdAndJockeyIdAndStatusIn(
            Long horseId,
            Long jockeyId,
            Collection<AssignmentStatus> statuses
    );

    boolean existsByRaceIdAndHorseIdAndJockeyIdAndStatusIn(
            Long raceId,
            Long horseId,
            Long jockeyId,
            Collection<AssignmentStatus> statuses
    );

    boolean existsByRaceIdAndHorseIdAndStatusIn(
            Long raceId,
            Long horseId,
            Collection<AssignmentStatus> statuses
    );

    boolean existsByJockeyIdAndStatus(Long jockeyId, AssignmentStatus status);

    boolean existsByHorseId(Long horseId);
}
