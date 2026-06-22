package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.RefereeInvitationRequest;
import com.minhthien.hoser_backend.dto.response.RefereeInvitationResponse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RefereeInvitation;
import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.RefereeInvitationRepository;
import com.minhthien.hoser_backend.repository.RefereeSalaryConfigRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RealtimeEventService;
import com.minhthien.hoser_backend.service.RefereeInvitationService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefereeInvitationServiceImpl implements RefereeInvitationService {
    private static final String REFERENCE_TYPE = "REFEREE_INVITATION";

    private final RefereeInvitationRepository invitationRepository;
    private final RaceRepository raceRepository;
    private final RaceResultRepository raceResultRepository;
    private final UserRepository userRepository;
    private final RefereeSalaryConfigRepository salaryConfigRepository;
    private final RefereePaymentService refereePaymentService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private RealtimeEventService realtimeEventService;

    @Autowired(required = false)
    void setRealtimeEventService(RealtimeEventService realtimeEventService) {
        this.realtimeEventService = realtimeEventService;
    }

    @Override
    @Transactional
    public RefereeInvitationResponse createInvitation(Long adminId, RefereeInvitationRequest request) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can create referee invitations");
        if (request == null) {
            throw new BadRequestException("Referee invitation request is required");
        }

        Race race = raceRepository.findByIdForUpdate(request.getRaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", request.getRaceId()));
        validateRaceCanBeInvited(race);
        if (race.getReferee() != null) {
            throw new BadRequestException("Race already has an assigned referee");
        }

        User referee = requireUser(request.getRefereeId());
        requireRole(referee, UserRole.REFEREE, "Invitation recipient must have REFEREE role");
        if (!Boolean.TRUE.equals(referee.getActive())) {
            throw new BadRequestException("Referee account is inactive");
        }
        validateRefereeAvailability(race, referee.getId());

        RefereeSalaryConfig salaryConfig = requireActiveSalaryConfig(request.getSalaryConfigId());
        if (invitationRepository.existsByRaceIdAndRefereeIdAndStatus(
                race.getId(), referee.getId(), AssignmentStatus.PENDING)) {
            throw new BadRequestException("A pending invitation already exists for this referee and race");
        }

        RefereeInvitation invitation = invitationRepository.save(RefereeInvitation.builder()
                .admin(admin)
                .referee(referee)
                .race(race)
                .salaryConfig(salaryConfig)
                .message(request.getMessage())
                .createdBy(admin.getUsername())
                .updatedBy(admin.getUsername())
                .build());
        notify(referee, NotificationType.REFEREE_INVITATION_CREATED,
                "Referee invitation received",
                "You were invited to referee race " + race.getName(), invitation);
        return map(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefereeInvitationResponse> getAdminInvitations(Long adminId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view referee invitations");
        return invitationRepository.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RefereeInvitationResponse getAdminInvitation(Long adminId, Long invitationId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view referee invitations");
        return map(requireDetailedInvitation(invitationId));
    }

    @Override
    @Transactional
    public RefereeInvitationResponse cancelInvitation(Long adminId, Long invitationId) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can cancel referee invitations");
        RefereeInvitation invitation = requireInvitationForUpdate(invitationId);
        requirePending(invitation);
        cancel(invitation, admin.getUsername(), "Invitation cancelled by admin");
        invitation = invitationRepository.save(invitation);
        notify(invitation.getReferee(), NotificationType.REFEREE_INVITATION_CANCELLED,
                "Referee invitation cancelled",
                "The invitation for race " + invitation.getRace().getName() + " was cancelled", invitation);
        return map(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefereeInvitationResponse> getRefereeInvitations(Long refereeId) {
        requireRole(requireUser(refereeId), UserRole.REFEREE,
                "Only referees can view referee invitations");
        return invitationRepository.findByRefereeIdOrderByCreatedAtDesc(refereeId).stream()
                .map(this::map)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RefereeInvitationResponse getRefereeInvitation(Long refereeId, Long invitationId) {
        requireRole(requireUser(refereeId), UserRole.REFEREE,
                "Only referees can view referee invitations");
        RefereeInvitation invitation = requireDetailedInvitation(invitationId);
        requireRecipient(invitation, refereeId);
        return map(invitation);
    }

    @Override
    @Transactional
    public RefereeInvitationResponse acceptInvitation(Long refereeId, Long invitationId,
                                                       InvitationDecisionRequest request) {
        RefereeInvitation candidate = requireDetailedInvitation(invitationId);
        requireRecipient(candidate, refereeId);
        Long raceId = candidate.getRace().getId();
        Race race = raceRepository.findByIdForUpdate(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
        User referee = userRepository.findByIdForUpdate(refereeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", refereeId));
        RefereeInvitation invitation = requireInvitationForUpdate(invitationId);
        requireRecipient(invitation, refereeId);
        requirePending(invitation);
        requireRole(referee, UserRole.REFEREE, "Only referees can accept referee invitations");
        if (!Boolean.TRUE.equals(referee.getActive())) {
            throw new BadRequestException("Referee account is inactive");
        }
        validateRaceCanBeInvited(race);
        if (race.getReferee() != null) {
            throw new BadRequestException("Race already has an assigned referee");
        }
        validateRefereeAvailability(race, refereeId);
        RefereeSalaryConfig salaryConfig = requireActiveSalaryConfig(invitation.getSalaryConfig().getId());
        invitation.setSalaryConfig(salaryConfig);

        refereePaymentService.reserveForAssignment(invitation.getAdmin().getId(), race, referee, salaryConfig.getId());
        race.setReferee(referee);
        raceRepository.save(race);

        invitation.setStatus(AssignmentStatus.ACCEPTED);
        invitation.setResponseNote(resolveNote(request));
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedBy(referee.getUsername());
        invitation = invitationRepository.save(invitation);
        cancelOtherPendingInvitations(invitation, referee);

        notify(invitation.getAdmin(), NotificationType.REFEREE_INVITATION_ACCEPTED,
                "Referee invitation accepted",
                referee.getUsername() + " accepted the invitation for race " + race.getName(), invitation);
        if (race.getTournament().getStatus() == TournamentStatus.SCHEDULED) {
            safeSendMail(() -> mailService.sendRaceScheduled(race, referee),
                    "accepted referee invitation", invitation.getId(), refereeId);
        }
        if (realtimeEventService != null) {
            realtimeEventService.publishRaceStatus(
                    race, "RACE_REFEREE_ASSIGNED", race.getStatus().name(), String.valueOf(race.getId()));
        }
        return map(invitation);
    }

    @Override
    @Transactional
    public RefereeInvitationResponse rejectInvitation(Long refereeId, Long invitationId,
                                                       InvitationDecisionRequest request) {
        User referee = requireUser(refereeId);
        requireRole(referee, UserRole.REFEREE, "Only referees can reject referee invitations");
        RefereeInvitation invitation = requireInvitationForUpdate(invitationId);
        requireRecipient(invitation, refereeId);
        requirePending(invitation);

        invitation.setStatus(AssignmentStatus.REJECTED);
        invitation.setResponseNote(resolveNote(request));
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedBy(referee.getUsername());
        invitation = invitationRepository.save(invitation);
        notify(invitation.getAdmin(), NotificationType.REFEREE_INVITATION_REJECTED,
                "Referee invitation rejected",
                referee.getUsername() + " rejected the invitation for race "
                        + invitation.getRace().getName(), invitation);
        return map(invitation);
    }

    @Override
    @Transactional
    public void cancelPendingInvitationsForRace(Long raceId, String reason) {
        List<RefereeInvitation> pending = invitationRepository
                .findByRaceIdAndStatusOrderByCreatedAtDesc(raceId, AssignmentStatus.PENDING);
        if (pending.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (RefereeInvitation invitation : pending) {
            invitation.setStatus(AssignmentStatus.CANCELLED);
            invitation.setResponseNote(reason);
            invitation.setCancelledAt(now);
            invitation.setUpdatedBy("SYSTEM");
        }
        invitationRepository.saveAll(pending);
        pending.forEach(invitation -> notify(invitation.getReferee(),
                NotificationType.REFEREE_INVITATION_CANCELLED,
                "Referee invitation cancelled",
                "The invitation for race " + invitation.getRace().getName() + " was cancelled", invitation));
    }

    private void cancelOtherPendingInvitations(RefereeInvitation accepted, User acceptedBy) {
        List<RefereeInvitation> pending = invitationRepository
                .findByRaceIdAndStatusAndIdNotOrderByCreatedAtDesc(
                        accepted.getRace().getId(), AssignmentStatus.PENDING, accepted.getId());
        if (pending.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (RefereeInvitation invitation : pending) {
            invitation.setStatus(AssignmentStatus.CANCELLED);
            invitation.setResponseNote("Another referee accepted this race invitation");
            invitation.setCancelledAt(now);
            invitation.setUpdatedBy(acceptedBy.getUsername());
        }
        invitationRepository.saveAll(pending);
        pending.forEach(invitation -> notify(invitation.getReferee(),
                NotificationType.REFEREE_INVITATION_CANCELLED,
                "Referee invitation cancelled",
                "Another referee accepted the invitation for race "
                        + invitation.getRace().getName(), invitation));
    }

    private void validateRaceCanBeInvited(Race race) {
        if (race.getStatus() == RaceStatus.CANCELLED) {
            throw new BadRequestException("Cannot invite a referee to a cancelled race");
        }
        if (race.getStatus() == RaceStatus.RESULT_CONFIRMED || raceResultRepository.existsByRaceId(race.getId())) {
            throw new BadRequestException("Cannot invite a referee after race result is finalized");
        }
    }

    private void validateRefereeAvailability(Race race, Long refereeId) {
        if (raceRepository.existsRefereeOverlapExcludingRace(refereeId, race.getId(),
                race.getScheduledStartAt(), race.getScheduledEndAt())) {
            throw new BadRequestException("Referee cannot be assigned to overlapping races");
        }
    }

    private RefereeSalaryConfig requireActiveSalaryConfig(Long salaryConfigId) {
        RefereeSalaryConfig salaryConfig = salaryConfigRepository.findById(salaryConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeSalaryConfig", "id", salaryConfigId));
        if (!Boolean.TRUE.equals(salaryConfig.getActive())) {
            throw new BadRequestException("Referee salary config is inactive");
        }
        BigDecimal amount = salaryConfig.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Referee per-race fee must be greater than zero");
        }
        return salaryConfig;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private RefereeInvitation requireDetailedInvitation(Long id) {
        return invitationRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeInvitation", "id", id));
    }

    private RefereeInvitation requireInvitationForUpdate(Long id) {
        return invitationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeInvitation", "id", id));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) throw new UnauthorizedException(message);
    }

    private void requireRecipient(RefereeInvitation invitation, Long refereeId) {
        if (!invitation.getReferee().getId().equals(refereeId)) {
            throw new UnauthorizedException("Cannot access another referee's invitation");
        }
    }

    private void requirePending(RefereeInvitation invitation) {
        if (invitation.getStatus() != AssignmentStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be updated");
        }
    }

    private void cancel(RefereeInvitation invitation, String updatedBy, String reason) {
        invitation.setStatus(AssignmentStatus.CANCELLED);
        invitation.setResponseNote(reason);
        invitation.setCancelledAt(LocalDateTime.now());
        invitation.setUpdatedBy(updatedBy);
    }

    private String resolveNote(InvitationDecisionRequest request) {
        return request == null ? null : request.getNote();
    }

    private void notify(User recipient, NotificationType type, String title, String message,
                        RefereeInvitation invitation) {
        notificationService.notify(recipient, type, title, message, REFERENCE_TYPE,
                String.valueOf(invitation.getId()),
                "{\"raceId\":%d,\"refereeId\":%d,\"adminId\":%d,\"status\":\"%s\"}".formatted(
                        invitation.getRace().getId(), invitation.getReferee().getId(),
                        invitation.getAdmin().getId(), invitation.getStatus()));
    }

    private void safeSendMail(Runnable action, String event, Long referenceId, Long recipientId) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Could not send email: event={}, referenceId={}, recipientId={}",
                    event, referenceId, recipientId, ex);
        }
    }

    private RefereeInvitationResponse map(RefereeInvitation invitation) {
        Race race = invitation.getRace();
        var venue = race.getVenue();
        RefereeSalaryConfig salaryConfig = invitation.getSalaryConfig();
        return RefereeInvitationResponse.builder()
                .id(invitation.getId())
                .adminId(invitation.getAdmin().getId())
                .adminUsername(invitation.getAdmin().getUsername())
                .refereeId(invitation.getReferee().getId())
                .refereeUsername(invitation.getReferee().getUsername())
                .raceId(race.getId())
                .raceName(race.getName())
                .raceScheduledStartAt(race.getScheduledStartAt())
                .raceScheduledEndAt(race.getScheduledEndAt())
                .venueId(venue == null ? null : venue.getId())
                .venueName(venue == null ? null : venue.getName())
                .venueAddress(venue == null ? null : venue.getAddress())
                .tournamentId(race.getTournament().getId())
                .tournamentName(race.getTournament().getName())
                .salaryConfigId(salaryConfig.getId())
                .salaryConfigName(salaryConfig.getName())
                .raceType(salaryConfig.getRaceType())
                .salaryAmount(salaryConfig.getAmount())
                .status(invitation.getStatus())
                .message(invitation.getMessage())
                .responseNote(invitation.getResponseNote())
                .respondedAt(invitation.getRespondedAt())
                .cancelledAt(invitation.getCancelledAt())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }
}
