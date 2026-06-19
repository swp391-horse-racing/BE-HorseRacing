package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.response.JockeyInvitationResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.JockeyInvitationService;
import com.minhthien.hoser_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JockeyInvitationServiceImpl implements JockeyInvitationService {
    private static final String JOCKEY_INVITATION_REFERENCE = "JOCKEY_INVITATION";

    private static final Set<AssignmentStatus> ACTIVE_STATUSES = Set.of(
            AssignmentStatus.PENDING,
            AssignmentStatus.ACCEPTED
    );
    private static final Set<RaceRegistrationStatus> ACTIVE_REGISTRATION_STATUSES = Set.of(
            RaceRegistrationStatus.PENDING,
            RaceRegistrationStatus.APPROVED
    );
    private static final Set<RaceStatus> FINISHED_RACE_STATUSES = Set.of(
            RaceStatus.RESULT_CONFIRMED,
            RaceStatus.CANCELLED
    );
    private static final Set<TournamentStatus> FINISHED_TOURNAMENT_STATUSES = Set.of(
            TournamentStatus.COMPLETED,
            TournamentStatus.CANCELLED
    );

    private final JockeyInvitationRepository jockeyInvitationRepository;
    private final HorseRepository horseRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private NotificationService notificationService;

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public JockeyInvitationResponse createInvitation(Long ownerId, JockeyInvitationRequest request) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can create jockey invitations");
        Horse horse = horseRepository.findByIdAndOwnerId(request.getHorseId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Horse", "id", request.getHorseId()));
        if (horse.getStatus() != HorseStatus.APPROVED) {
            throw new BadRequestException("Horse must be approved before inviting a jockey");
        }
        Race race = raceRepository.findById(request.getRaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", request.getRaceId()));
        validateInvitableRace(race);
        if (jockeyInvitationRepository.existsByRaceIdAndOwnerIdAndStatusIn(
                race.getId(), ownerId, ACTIVE_STATUSES)) {
            throw new BadRequestException("Owner already has an active jockey invitation for this race");
        }
        validateOwnerTournamentCapacity(owner, race);

        User jockey = requireUser(request.getJockeyId());
        requireRole(jockey, UserRole.JOCKEY, "Invitation target must be a jockey");
        JockeyProfile profile = jockeyProfileRepository.findByUserId(jockey.getId())
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", jockey.getId()));
        if (profile.getStatus() != JockeyStatus.APPROVED) {
            throw new BadRequestException("Jockey profile must be approved before invitation");
        }
        validateJockeyRaceAvailability(jockey, race);
        if (jockeyInvitationRepository.existsActiveHorseRaceConflict(
                horse.getId(), race.getId(), race.getScheduledStartAt(), race.getScheduledEndAt(),
                ACTIVE_STATUSES, FINISHED_RACE_STATUSES, FINISHED_TOURNAMENT_STATUSES)) {
            throw new DuplicateResourceException("Active invitation already exists for this horse in this race or an overlapping race");
        }

        JockeyInvitation invitation = JockeyInvitation.builder()
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .race(race)
                .jockeyProfile(profile)
                .status(AssignmentStatus.PENDING)
                .message(request.getMessage())
                .remunerationAmount(request.getRemunerationAmount())
                .createdBy(owner.getUsername())
                .updatedBy(owner.getUsername())
                .build();
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getJockey(), NotificationType.INVITATION_CREATED,
                "New jockey invitation",
                "You received a jockey invitation for horse " + invitation.getHorse().getName(),
                invitation);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyInvitationResponse> getOwnerInvitations(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view owner invitations");
        return jockeyInvitationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyInvitationResponse getOwnerInvitation(Long ownerId, Long invitationId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view owner invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Cannot view another owner's invitation");
        }
        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyInvitationResponse> getOwnerAcceptedJockeys(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view owner jockeys");
        return jockeyInvitationRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                        ownerId, AssignmentStatus.ACCEPTED).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public JockeyInvitationResponse cancelInvitation(Long ownerId, Long invitationId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can cancel owner invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Cannot cancel another owner's invitation");
        }
        if (invitation.getStatus() != AssignmentStatus.PENDING
                && invitation.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new BadRequestException("Only pending or accepted invitations can be cancelled");
        }
        if (invitation.getStatus() == AssignmentStatus.ACCEPTED
                && raceRegistrationRepository.existsByJockeyInvitationIdAndStatusIn(
                invitation.getId(), ACTIVE_REGISTRATION_STATUSES)) {
            throw new BadRequestException("Cannot cancel an accepted invitation used in an active race registration");
        }

        invitation.setStatus(AssignmentStatus.CANCELLED);
        invitation.setCancelledAt(LocalDateTime.now());
        invitation.setUpdatedBy(owner.getUsername());
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getJockey(), NotificationType.INVITATION_CANCELLED,
                "Jockey invitation cancelled",
                "The invitation for horse " + invitation.getHorse().getName() + " was cancelled",
                invitation);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyInvitationResponse> getJockeyInvitations(Long jockeyId) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can view jockey invitations");
        return jockeyInvitationRepository.findByJockeyIdOrderByCreatedAtDesc(jockeyId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyInvitationResponse getJockeyInvitation(Long jockeyId, Long invitationId) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can view jockey invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        requireInvitationOwner(invitation, jockeyId);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional
    public JockeyInvitationResponse acceptInvitation(Long jockeyId, Long invitationId, InvitationDecisionRequest request) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can respond to jockey invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        requireInvitationOwner(invitation, jockeyId);
        requirePending(invitation);
        requireStillEligible(invitation);
        if (invitation.getRace() != null) {
            validateJockeyRaceAvailability(jockey, invitation.getRace());
        }

        invitation.setStatus(AssignmentStatus.ACCEPTED);
        invitation.setResponseNote(resolveNote(request));
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedBy(jockey.getUsername());
        invitation = jockeyInvitationRepository.save(invitation);
        cancelOtherPendingInvitations(invitation, jockey);
        notify(invitation.getOwner(), NotificationType.INVITATION_ACCEPTED,
                "Jockey invitation accepted",
                invitation.getJockey().getUsername() + " accepted your invitation",
                invitation);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional
    public JockeyInvitationResponse rejectInvitation(Long jockeyId, Long invitationId, InvitationDecisionRequest request) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can respond to jockey invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        requireInvitationOwner(invitation, jockeyId);
        requirePending(invitation);

        invitation.setStatus(AssignmentStatus.REJECTED);
        invitation.setResponseNote(resolveNote(request));
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedBy(jockey.getUsername());
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getOwner(), NotificationType.INVITATION_REJECTED,
                "Jockey invitation rejected",
                invitation.getJockey().getUsername() + " rejected your invitation",
                invitation);
        return mapToResponse(invitation);
    }

    private void cancelOtherPendingInvitations(JockeyInvitation acceptedInvitation, User jockey) {
        LocalDateTime now = LocalDateTime.now();
        List<JockeyInvitation> otherPendingInvitations =
                jockeyInvitationRepository.findByJockeyIdAndStatusAndIdNotOrderByCreatedAtDesc(
                        jockey.getId(), AssignmentStatus.PENDING, acceptedInvitation.getId());
        List<JockeyInvitation> conflictingPendingInvitations = otherPendingInvitations.stream()
                .filter(invitation -> conflictsWithAcceptedInvitation(invitation, acceptedInvitation))
                .toList();
        if (conflictingPendingInvitations.isEmpty()) {
            return;
        }
        for (JockeyInvitation invitation : conflictingPendingInvitations) {
            invitation.setStatus(AssignmentStatus.CANCELLED);
            invitation.setResponseNote("Jockey accepted a conflicting invitation");
            invitation.setCancelledAt(now);
            invitation.setUpdatedBy(jockey.getUsername());
        }
        List<JockeyInvitation> cancelledInvitations = jockeyInvitationRepository.saveAll(conflictingPendingInvitations);
        for (JockeyInvitation invitation : cancelledInvitations) {
            notify(invitation.getOwner(), NotificationType.INVITATION_CANCELLED,
                    "Jockey invitation cancelled",
                    invitation.getJockey().getUsername() + " accepted a conflicting invitation",
                    invitation);
        }
    }

    private void validateJockeyRaceAvailability(User jockey, Race race) {
        if (jockeyInvitationRepository.existsAcceptedJockeyRaceConflict(jockey.getId(), race.getId(),
                race.getScheduledStartAt(), race.getScheduledEndAt(), AssignmentStatus.ACCEPTED,
                FINISHED_RACE_STATUSES, FINISHED_TOURNAMENT_STATUSES)) {
            throw new BadRequestException("Jockey already accepted an invitation for this race or an overlapping race");
        }
    }

    private void validateOwnerTournamentCapacity(User owner, Race race) {
        Long tournamentId = race.getTournament().getId();
        long activeRegistrationCount = raceRegistrationRepository.countByRaceTournamentIdAndOwnerIdAndStatusIn(
                tournamentId, owner.getId(), ACTIVE_REGISTRATION_STATUSES);
        long unusedInvitationCount = jockeyInvitationRepository.countUnusedActiveByOwnerTournament(
                owner.getId(), tournamentId, ACTIVE_STATUSES, ACTIVE_REGISTRATION_STATUSES);
        if (activeRegistrationCount + unusedInvitationCount >= race.getTournament().getMaxHorsesPerOwner()) {
            throw new BadRequestException("Owner has reached the maximum horses allowed for this tournament");
        }
    }

    private boolean conflictsWithAcceptedInvitation(JockeyInvitation invitation,
                                                    JockeyInvitation acceptedInvitation) {
        Race invitationRace = invitation.getRace();
        Race acceptedRace = acceptedInvitation.getRace();
        if (invitationRace == null || acceptedRace == null) {
            return false;
        }
        if (invitationRace.getId().equals(acceptedRace.getId())) {
            return true;
        }
        return isUnfinishedRaceContext(invitationRace)
                && isUnfinishedRaceContext(acceptedRace)
                && schedulesOverlap(invitationRace, acceptedRace);
    }

    private boolean isUnfinishedRaceContext(Race race) {
        return !FINISHED_RACE_STATUSES.contains(race.getStatus())
                && race.getTournament() != null
                && !FINISHED_TOURNAMENT_STATUSES.contains(race.getTournament().getStatus());
    }

    private boolean schedulesOverlap(Race firstRace, Race secondRace) {
        return firstRace.getScheduledStartAt().isBefore(secondRace.getScheduledEndAt())
                && firstRace.getScheduledEndAt().isAfter(secondRace.getScheduledStartAt());
    }

    private void notify(User recipient, NotificationType type, String title, String message,
                        JockeyInvitation invitation) {
        if (notificationService == null) {
            return;
        }
        notificationService.notify(recipient, type, title, message, JOCKEY_INVITATION_REFERENCE,
                String.valueOf(invitation.getId()),
                "{\"horseId\":%d,\"jockeyId\":%d,\"ownerId\":%d}".formatted(
                        invitation.getHorse().getId(), invitation.getJockey().getId(), invitation.getOwner().getId()));
    }

    private void requireStillEligible(JockeyInvitation invitation) {
        if (invitation.getHorse().getStatus() != HorseStatus.APPROVED) {
            throw new BadRequestException("Horse is no longer approved");
        }
        if (invitation.getRace() != null) {
            validateInvitableRace(invitation.getRace());
        }
        if (invitation.getJockeyProfile().getStatus() != JockeyStatus.APPROVED) {
            throw new BadRequestException("Jockey profile is no longer approved");
        }
    }

    private void validateInvitableRace(Race race) {
        boolean tournamentIsPublic = race.getTournament().getStatus() == TournamentStatus.PUBLISHED
                || race.getTournament().getStatus() == TournamentStatus.OPEN_REGISTRATION;
        boolean raceIsInvitable = race.getStatus() == RaceStatus.PUBLISHED
                || race.getStatus() == RaceStatus.OPEN_REGISTRATION;
        if (!tournamentIsPublic || !raceIsInvitable) {
            throw new BadRequestException("Race is not open for jockey invitation");
        }
    }

    private void requireInvitationOwner(JockeyInvitation invitation, Long jockeyId) {
        if (!invitation.getJockey().getId().equals(jockeyId)) {
            throw new UnauthorizedException("Cannot respond to another jockey's invitation");
        }
    }

    private void requirePending(JockeyInvitation invitation) {
        if (invitation.getStatus() != AssignmentStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be updated");
        }
    }

    private String resolveNote(InvitationDecisionRequest request) {
        return request == null ? null : request.getNote();
    }

    private JockeyInvitation requireInvitation(Long invitationId) {
        return jockeyInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyInvitation", "id", invitationId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) {
            throw new UnauthorizedException(message);
        }
    }

    private JockeyInvitationResponse mapToResponse(JockeyInvitation invitation) {
        Race race = invitation.getRace();
        var venue = race == null ? null : race.getVenue();
        return JockeyInvitationResponse.builder()
                .id(invitation.getId())
                .ownerId(invitation.getOwner().getId())
                .ownerUsername(invitation.getOwner().getUsername())
                .jockeyId(invitation.getJockey().getId())
                .jockeyUsername(invitation.getJockey().getUsername())
                .jockeyProfileId(invitation.getJockeyProfile().getId())
                .horseId(invitation.getHorse().getId())
                .horseName(invitation.getHorse().getName())
                .raceId(race == null ? null : race.getId())
                .raceName(race == null ? null : race.getName())
                .raceScheduledStartAt(race == null ? null : race.getScheduledStartAt())
                .raceScheduledEndAt(race == null ? null : race.getScheduledEndAt())
                .venueId(venue == null ? null : venue.getId())
                .venueName(venue == null ? null : venue.getName())
                .venueAddress(venue == null ? null : venue.getAddress())
                .tournamentId(race == null ? null : race.getTournament().getId())
                .tournamentName(race == null ? null : race.getTournament().getName())
                .status(invitation.getStatus())
                .message(invitation.getMessage())
                .responseNote(invitation.getResponseNote())
                .remunerationAmount(invitation.getRemunerationAmount())
                .respondedAt(invitation.getRespondedAt())
                .cancelledAt(invitation.getCancelledAt())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }
}
