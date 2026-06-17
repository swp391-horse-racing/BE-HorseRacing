package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    @Query("""
            select count(invitation) > 0
            from JockeyInvitation invitation
            where invitation.jockey.id = :jockeyId
              and invitation.status = :status
              and invitation.race is not null
              and invitation.race.status not in :finishedRaceStatuses
              and invitation.race.tournament.status not in :finishedTournamentStatuses
              and (
                    invitation.race.id = :raceId
                    or (
                        invitation.race.scheduledStartAt < :scheduledEndAt
                        and invitation.race.scheduledEndAt > :scheduledStartAt
                    )
              )
            """)
    boolean existsAcceptedJockeyRaceConflict(@Param("jockeyId") Long jockeyId,
                                             @Param("raceId") Long raceId,
                                             @Param("scheduledStartAt") LocalDateTime scheduledStartAt,
                                             @Param("scheduledEndAt") LocalDateTime scheduledEndAt,
                                             @Param("status") AssignmentStatus status,
                                             @Param("finishedRaceStatuses") Collection<RaceStatus> finishedRaceStatuses,
                                             @Param("finishedTournamentStatuses")
                                             Collection<TournamentStatus> finishedTournamentStatuses);

    boolean existsByHorseId(Long horseId);
}
