package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceFinalizeResultRequest;
import com.minhthien.hoser_backend.dto.request.RaceCancellationRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintResolveRequest;
import com.minhthien.hoser_backend.dto.request.RaceGateUpdateRequest;
import com.minhthien.hoser_backend.dto.request.RaceParticipantCheckInRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationReviewRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationWithdrawRequest;
import com.minhthien.hoser_backend.dto.request.RaceResultEntryRequest;
import com.minhthien.hoser_backend.dto.request.RaceViolationRequest;
import com.minhthien.hoser_backend.dto.response.JockeyChallengeStandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceComplaintResponse;
import com.minhthien.hoser_backend.dto.response.RaceParticipantResponse;
import com.minhthien.hoser_backend.dto.response.RaceRegistrationResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResultResponse;
import com.minhthien.hoser_backend.dto.response.RaceViolationResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.dto.response.ViolationPenaltyRuleResponse;
import com.minhthien.hoser_backend.dto.response.ViolationTypeOptionResponse;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RaceDayService;
import com.minhthien.hoser_backend.service.RealtimeEventService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import com.minhthien.hoser_backend.service.SystemSettingsService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceDayServiceImpl implements RaceDayService {
    private static final String RACE_REGISTRATION_REF = "RACE_REGISTRATION";
    private static final String RACE_RESULT_REF = "RACE_RESULT";
    private static final String JOCKEY_CHALLENGE_REF = "JOCKEY_CHALLENGE";
    private static final String RACE_COMPLAINT_REF = "RACE_COMPLAINT";
    private static final String RACE_COMPLAINT_EVIDENCE_FOLDER = "hoser/race-complaints/evidence";
    private static final String RACE_VIOLATION_EVIDENCE_FOLDER = "hoser/race-violations/evidence";
    private static final long MAX_VIOLATION_EVIDENCE_BYTES = 100L * 1024L * 1024L;
    private final RaceRepository raceRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceComplaintRepository raceComplaintRepository;
    private final RaceViolationRepository raceViolationRepository;
    private final JockeyChallengeResultRepository jockeyChallengeResultRepository;
    private final JockeyInvitationRepository jockeyInvitationRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final TournamentServiceImpl tournamentService;
    private final MailService mailService;
    private final BettingService bettingService;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final RefereePaymentService refereePaymentService;
    private final SystemSettingsService systemSettingsService;
    private final RaceCancellationService raceCancellationService;
    private NotificationService notificationService;
    private RealtimeEventService realtimeEventService;

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired(required = false)
    void setRealtimeEventService(RealtimeEventService realtimeEventService) {
        this.realtimeEventService = realtimeEventService;
    }

    @Override
    @Transactional
    public RaceRegistrationResponse registerForRace(Long ownerId, Long raceId, RaceRegistrationRequest request) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can register for races");
        if (request == null) {
            throw new BadRequestException("Race registration request is required");
        }
        Race race = requireRace(raceId);
        Tournament tournament = race.getTournament();
        if (tournament.getStatus() != TournamentStatus.OPEN_REGISTRATION) {
            throw new BadRequestException("Tournament registration is not open");
        }
        if (owner.getOwnerBanUntil() != null && owner.getOwnerBanUntil().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Owner is banned from race registration until " + owner.getOwnerBanUntil());
        }
        JockeyInvitation invitation = jockeyInvitationRepository.findById(request.getJockeyInvitationId())
                .orElseThrow(() -> new ResourceNotFoundException("JockeyInvitation", "id",
                        request.getJockeyInvitationId()));
        validateEligibleInvitation(ownerId, raceId, request.getHorseId(), invitation);
        List<RaceRegistrationStatus> activeStatuses = activeRegistrationStatuses();
        if (raceRegistrationRepository.existsByRaceIdAndOwnerIdAndStatusIn(raceId, ownerId, activeStatuses)) {
            throw new BadRequestException("Owner can only register one horse for this race");
        }
        long ownerTournamentRegistrationCount = raceRegistrationRepository
                .countByRaceTournamentIdAndOwnerIdAndStatusIn(tournament.getId(), ownerId, activeStatuses);
        if (ownerTournamentRegistrationCount >= tournament.getMaxHorsesPerOwner()) {
            throw new BadRequestException("Owner has reached the maximum horses allowed for this tournament");
        }
        if (raceRegistrationRepository.existsByRaceIdAndHorseIdAndStatusIn(
                raceId, invitation.getHorse().getId(), activeStatuses)) {
            throw new BadRequestException("Horse is already registered for this race");
        }
        LocalDateTime raceStartAt = race.getScheduledStartAt();
        if (raceRegistrationRepository.existsActiveHorseRegistrationWithinWindow(invitation.getHorse().getId(),
                activeStatuses, raceStartAt.minusHours(24), raceStartAt.plusHours(24))) {
            throw new BadRequestException("Horse can only join one race within a 24-hour period");
        }
        if (raceRegistrationRepository.existsActiveJockeyOverlap(invitation.getJockey().getId(),
                activeStatuses, race.getScheduledStartAt(), race.getScheduledEndAt())) {
            throw new BadRequestException("Jockey cannot join overlapping races");
        }

        RaceRegistration registration = RaceRegistration.builder()
                .race(race)
                .owner(owner)
                .horse(invitation.getHorse())
                .jockey(invitation.getJockey())
                .jockeyInvitation(invitation)
                .entryFeeAmount(race.getEntryFee())
                .ownerNote(request.getNote())
                .build();
        RaceRegistration saved = raceRegistrationRepository.save(registration);
        debitRegistrationFee(saved);
        notifyRegistrationCreated(saved);
        return mapRegistration(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceRegistrationResponse> getOwnerRaceRegistrations(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view race registrations");
        return raceRegistrationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::mapRegistration)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceRegistrationResponse> getAdminTournamentRaceRegistrations(Long adminId, Long tournamentId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view race registrations");
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        return raceRegistrationRepository.findByRaceTournamentIdOrderByCreatedAtDesc(tournamentId).stream()
                .map(this::mapRegistration)
                .toList();
    }

    @Override
    @Transactional
    public RaceRegistrationResponse approveRaceRegistration(Long adminId, Long registrationId,
                                                            RaceRegistrationReviewRequest request) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can approve race registrations");
        RaceRegistration registration = requireRegistration(registrationId);
        if (registration.getStatus() != RaceRegistrationStatus.PENDING) {
            throw new BadRequestException("Only pending race registrations can be approved");
        }
        Race race = registration.getRace();
        if (race.getParticipants().size() >= race.getMaxParticipants()) {
            throw new BadRequestException("Race is already full");
        }
        registration.setStatus(RaceRegistrationStatus.APPROVED);
        registration.setReviewedBy(adminId);
        registration.setReviewedAt(LocalDateTime.now());
        registration.setReviewNote(request == null ? null : request.getNote());
        RaceRegistration saved = raceRegistrationRepository.save(registration);
        RaceParticipant participant = RaceParticipant.builder()
                .race(race)
                .registration(saved)
                .owner(saved.getOwner())
                .horse(saved.getHorse())
                .jockey(saved.getJockey())
                .status(RaceParticipantStatus.REGISTERED)
                .build();
        raceParticipantRepository.save(participant);
        notifyRegistrationStatus(saved, NotificationType.REGISTRATION_APPROVED, "Race registration approved",
                "Your registration for race " + saved.getRace().getName() + " was approved", true);
        return mapRegistration(saved);
    }

    @Override
    @Transactional
    public RaceRegistrationResponse rejectRaceRegistration(Long adminId, Long registrationId,
                                                           RaceRegistrationReviewRequest request) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can reject race registrations");
        RaceRegistration registration = requireRegistration(registrationId);
        if (registration.getStatus() != RaceRegistrationStatus.PENDING) {
            throw new BadRequestException("Only pending race registrations can be rejected");
        }
        refundRegistrationFee(registration, "Race entry fee refunded after rejection");
        registration.setStatus(RaceRegistrationStatus.REJECTED);
        registration.setReviewedBy(adminId);
        registration.setReviewedAt(LocalDateTime.now());
        registration.setReviewNote(request == null ? null : request.getNote());
        RaceRegistration saved = raceRegistrationRepository.save(registration);
        notifyRegistrationStatus(saved, NotificationType.REGISTRATION_REJECTED, "Race registration rejected",
                "Your registration for race " + saved.getRace().getName() + " was rejected", true);
        return mapRegistration(saved);
    }

    @Override
    @Transactional
    public RaceRegistrationResponse withdrawRaceRegistration(Long ownerId, Long registrationId,
                                                             RaceRegistrationWithdrawRequest request) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can withdraw race registrations");
        RaceRegistration registration = requireRegistration(registrationId);
        if (!registration.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Cannot withdraw another owner's race registration");
        }
        if (registration.getStatus() != RaceRegistrationStatus.PENDING) {
            throw new BadRequestException("Only pending race registrations can be withdrawn");
        }
        refundRegistrationFee(registration, "Race entry fee refunded after owner withdrawal");
        registration.setStatus(RaceRegistrationStatus.WITHDRAWN);
        registration.setWithdrawNote(request == null ? null : request.getNote());
        RaceRegistration saved = raceRegistrationRepository.save(registration);
        notifyRegistrationStatus(saved, NotificationType.REGISTRATION_WITHDRAWN, "Race registration withdrawn",
                "Your registration for race " + saved.getRace().getName() + " was withdrawn", false);
        return mapRegistration(saved);
    }

    @Override
    @Transactional
    public TournamentResponse scheduleTournament(Long adminId, Long tournamentId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can schedule tournaments");
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        if (tournament.getStatus() != TournamentStatus.REGISTRATION_CLOSED) {
            throw new BadRequestException("Tournament registration must be closed before scheduling");
        }
        List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournamentId);
        if (races.isEmpty()) {
            throw new BadRequestException("Tournament must have races before scheduling");
        }
        List<Race> schedulableRaces = races.stream()
                .filter(race -> race.getStatus() != RaceStatus.CANCELLED)
                .toList();
        if (schedulableRaces.isEmpty()) {
            throw new BadRequestException("Tournament has no active races to schedule");
        }
        long participantCount = raceParticipantRepository.countByRaceTournamentId(tournamentId);
        if (participantCount < tournament.getMinTeams()) {
            throw new BadRequestException("Tournament does not have enough approved participants");
        }
        if (participantCount > tournament.getMaxTeams()) {
            throw new BadRequestException("Tournament exceeds maximum team limit");
        }
        validateOwnerHorseMinimums(tournament);
        schedulableRaces.forEach(this::validateRaceReadyForSchedule);
        validateJockeyScheduleAcrossTournament(schedulableRaces);
        tournament.setStatus(TournamentStatus.SCHEDULED);
        schedulableRaces.forEach(race -> race.setStatus(RaceStatus.SCHEDULED));
        Tournament saved = tournamentRepository.save(tournament);
        schedulableRaces.forEach(this::sendRaceScheduledEmails);
        schedulableRaces.forEach(this::publishRaceScheduled);
        return tournamentService.mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceParticipantResponse> getRaceParticipants(Long adminId, Long raceId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view race participants");
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race", "id", raceId);
        }
        return raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId).stream()
                .map(this::mapParticipant)
                .toList();
    }

    @Override
    @Transactional
    public RaceResponse cancelRace(Long adminId, Long raceId, RaceCancellationRequest request) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can cancel races");
        Race race = raceRepository.findByIdForUpdate(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
        if (!canCancelRace(race.getStatus())) {
            throw new BadRequestException("Only pre-race or scheduled races can be cancelled");
        }
        String reason = request == null || request.getNote() == null || request.getNote().isBlank()
                ? "Race cancelled by administrator"
                : request.getNote().trim();
        raceCancellationService.cancelRace(raceId, adminId, reason,
                requireUser(adminId).getUsername(), true);
        publishRaceStatus(race, "RACE_CANCELLED");
        return tournamentService.mapRace(race);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getRefereeRaces(Long refereeId) {
        User referee = requireUser(refereeId);
        requireRole(referee, UserRole.REFEREE, "Only referees can view assigned races");
        return raceRepository.findByRefereeIdOrderByScheduledStartAtAsc(refereeId).stream()
                .map(tournamentService::mapRace)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getTodayRefereeRaces(Long refereeId) {
        User referee = requireUser(refereeId);
        requireRole(referee, UserRole.REFEREE, "Only referees can view assigned races");
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfTomorrow = today.plusDays(1).atStartOfDay();
        return raceRepository.findByRefereeIdAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
                        refereeId, startOfDay, startOfTomorrow).stream()
                .map(tournamentService::mapRace)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceParticipantResponse> getRefereeRaceParticipants(Long refereeId, Long raceId) {
        requireAssignedRefereeRace(refereeId, raceId);
        return raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId).stream()
                .map(this::mapParticipant)
                .toList();
    }

    @Override
    @Transactional
    public RaceParticipantResponse updateRefereeParticipantGate(Long refereeId, Long raceId, Long participantId,
                                                               RaceGateUpdateRequest request) {
        Race race = requireAssignedRefereeRace(refereeId, raceId);
        if (request == null || request.getGateNumber() == null) {
            throw new BadRequestException("Gate number is required");
        }
        if (request.getGateNumber() <= 0) {
            throw new BadRequestException("Gate number must be greater than zero");
        }
        if (race.getStatus() == RaceStatus.ONGOING
                || race.getStatus() == RaceStatus.RESULT_CONFIRMED
                || race.getStatus() == RaceStatus.CANCELLED
                || raceResultRepository.existsByRaceId(raceId)) {
            throw new BadRequestException("Cannot update gate after race has started, finished, or been cancelled");
        }
        RaceParticipant participant = raceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("RaceParticipant", "id", participantId));
        if (!participant.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Participant does not belong to this race");
        }
        if (participant.getStatus() != RaceParticipantStatus.CHECKED_IN) {
            throw new BadRequestException("Only checked-in participants can be assigned a gate");
        }
        if (raceParticipantRepository.existsByRaceIdAndGateNumberAndIdNot(
                raceId, request.getGateNumber(), participantId)) {
            throw new BadRequestException("Gate number already exists in this race");
        }
        participant.setGateNumber(request.getGateNumber());
        return mapParticipant(raceParticipantRepository.save(participant));
    }

    @Override
    @Transactional
    public RaceParticipantResponse checkInRaceParticipant(Long refereeId, Long raceId, Long participantId,
                                                          RaceParticipantCheckInRequest request) {
        Race race = requireAssignedRefereeRace(refereeId, raceId);
        if (race.getStatus() != RaceStatus.SCHEDULED) {
            throw new BadRequestException("Only scheduled races can be checked in");
        }
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Check-in status is required");
        }
        if (!List.of(RaceParticipantStatus.CHECKED_IN, RaceParticipantStatus.ABSENT,
                RaceParticipantStatus.DISQUALIFIED).contains(request.getStatus())) {
            throw new BadRequestException("Invalid check-in status");
        }
        RaceParticipant participant = raceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("RaceParticipant", "id", participantId));
        if (!participant.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Participant does not belong to this race");
        }
        LocalDateTime checkedInAt = LocalDateTime.now();
        participant.setStatus(request.getStatus());
        if (request.getStatus() != RaceParticipantStatus.CHECKED_IN) {
            participant.setGateNumber(null);
        }
        participant.setCheckInNote(request.getNote());
        participant.setCheckedInAt(checkedInAt);
        participant.setCheckedInBy(refereeId);
        RaceParticipant saved = raceParticipantRepository.save(participant);
        notifyParticipantCheckIn(saved);
        return mapParticipant(saved);
    }

    @Override
    @Transactional
    public RaceResponse startRace(Long refereeId, Long raceId) {
        Race race = requireAssignedRefereeRace(refereeId, raceId);
        if (race.getStatus() != RaceStatus.SCHEDULED) {
            throw new BadRequestException("Only scheduled races can be started");
        }
        if (race.getTournament() == null || race.getTournament().getStatus() != TournamentStatus.ONGOING) {
            throw new BadRequestException("Tournament must be ongoing before a race can be started");
        }
        List<RaceParticipant> participants = raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId);
        validateRaceGatesReady(participants);
        long checkedInCount = participants.stream()
                .filter(participant -> participant.getStatus() == RaceParticipantStatus.CHECKED_IN)
                .count();
        if (checkedInCount < race.getMinParticipants()) {
            throw new BadRequestException("Race does not have enough checked-in participants");
        }
        autoMarkRegisteredParticipantsAbsent(participants, refereeId);
        race.setStatus(RaceStatus.ONGOING);
        Race saved = raceRepository.save(race);
        bettingService.lockRaceBets(raceId);
        notifyRaceEvent(saved, NotificationType.RACE_STARTED, "Race started",
                "Race " + saved.getName() + " has started");
        publishRaceStatus(saved, "RACE_STARTED");
        return tournamentService.mapRace(saved);
    }

    @Override
    @Transactional
    public List<RaceResultResponse> finalizeRaceResult(Long refereeId, Long raceId, RaceFinalizeResultRequest request) {
        Race race = requireAssignedRefereeRace(refereeId, raceId);
        if (race.getStatus() != RaceStatus.ONGOING) {
            throw new BadRequestException("Only ongoing races can be finalized");
        }
        if (race.getStatus() == RaceStatus.RESULT_CONFIRMED || raceResultRepository.existsByRaceId(raceId)) {
            throw new BadRequestException("Race result has already been finalized");
        }
        if (request == null || request.getResults() == null || request.getResults().isEmpty()) {
            throw new BadRequestException("Race results are required");
        }
        List<RaceParticipant> participants = raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId);
        if (participants.isEmpty()) {
            throw new BadRequestException("Race has no approved participants");
        }
        Map<Long, RaceParticipant> participantById = participants.stream()
                .collect(Collectors.toMap(RaceParticipant::getId, Function.identity()));
        List<RaceResultEntryRequest> resultEntries = completeResultEntries(request.getResults(), participants);
        List<RaceViolation> violations = raceViolationRepository.findByRaceIdOrderByOccurredAtDesc(raceId);
        resultEntries = applyViolationResultActions(resultEntries, violations);
        validateResultEntries(resultEntries, participantById);

        LocalDateTime now = LocalDateTime.now();
        List<RaceResult> results = resultEntries.stream()
                .map(entry -> buildRaceResult(race, participantById.get(entry.getParticipantId()), entry, refereeId, now))
                .toList();
        raceResultRepository.saveAll(results);
        results.forEach(this::payoutRacePrize);
        race.setStatus(RaceStatus.RESULT_CONFIRMED);
        race.setResultFinalizedAt(now);
        race.setResultFinalizedBy(refereeId);
        raceRepository.save(race);
        try {
            refereePaymentService.payForCompletedRace(race);
        } catch (BadRequestException ex) {
            log.warn("Referee salary payout skipped for race {}: {}", race.getId(), ex.getMessage());
        }
        bettingService.settleRaceBets(raceId);
        List<RaceResult> savedResults = raceResultRepository.findByRaceIdOrderByRankAsc(raceId);
        publishRaceResults(race);
        notifyRaceResults(race, savedResults);
        return savedResults.stream()
                .map(this::mapResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultResponse> getRaceResults(Long raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race", "id", raceId);
        }
        return raceResultRepository.findByRaceIdOrderByRankAsc(raceId).stream()
                .map(this::mapResult)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceViolationResponse> getRefereeViolations(Long refereeId) {
        User referee = requireUser(refereeId);
        requireRole(referee, UserRole.REFEREE, "Only referees can view race violations");
        return raceViolationRepository.findByRefereeIdOrderByOccurredAtDesc(refereeId).stream()
                .map(this::mapViolation)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceViolationResponse> getRefereeRaceViolations(Long refereeId, Long raceId) {
        requireAssignedRefereeRace(refereeId, raceId);
        return raceViolationRepository.findByRaceIdOrderByOccurredAtDesc(raceId).stream()
                .map(this::mapViolation)
                .toList();
    }

    @Override
    @Transactional
    public RaceViolationResponse createRaceViolation(Long refereeId, Long raceId, RaceViolationRequest request,
                                                     MultipartFile evidence) {
        User referee = requireUser(refereeId);
        Race race = requireAssignedRefereeRace(refereeId, raceId);
        validateRaceCanAcceptViolation(race);
        RaceParticipant participant = requireRaceViolationParticipant(raceId, request);
        validateViolationEvidence(evidence, true);
        ViolationPenaltyRuleResponse rule = ruleForSeverity(request.getSeverity());
        ViolationTypeOptionResponse type = systemSettingsService.requireActiveViolationType(request.getType());
        String evidenceUrl = cloudinaryUploadService.upload(evidence, RACE_VIOLATION_EVIDENCE_FOLDER);
        RaceViolation violation = RaceViolation.builder()
                .race(race)
                .participant(participant)
                .owner(participant.getOwner())
                .horse(participant.getHorse())
                .jockey(participant.getJockey())
                .referee(referee)
                .type(type.getCode())
                .typeLabel(type.getLabel())
                .severity(request.getSeverity())
                .description(request.getDescription().trim())
                .penaltyText(trimToNull(request.getPenaltyText()))
                .occurredAt(request.getOccurredAt())
                .resultAction(rule.getResultAction())
                .timePenaltyMillis(defaultLong(rule.getTimePenaltyMillis()))
                .evidenceUrl(evidenceUrl)
                .evidenceName(safeEvidenceName(evidence))
                .evidenceType(safeEvidenceType(evidence))
                .evidenceSize(evidence.getSize())
                .build();
        return mapViolation(raceViolationRepository.save(violation));
    }

    @Override
    @Transactional
    public RaceViolationResponse updateRaceViolation(Long refereeId, Long raceId, Long violationId,
                                                     RaceViolationRequest request, MultipartFile evidence) {
        Race race = requireAssignedRefereeRace(refereeId, raceId);
        validateRaceCanAcceptViolation(race);
        RaceViolation violation = raceViolationRepository.findById(violationId)
                .orElseThrow(() -> new ResourceNotFoundException("RaceViolation", "id", violationId));
        if (!violation.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Violation does not belong to this race");
        }
        if (!violation.getReferee().getId().equals(refereeId)) {
            throw new UnauthorizedException("Cannot update another referee's race violation");
        }
        RaceParticipant participant = requireRaceViolationParticipant(raceId, request);
        validateViolationEvidence(evidence, false);
        ViolationPenaltyRuleResponse rule = ruleForSeverity(request.getSeverity());
        ViolationTypeOptionResponse type = systemSettingsService.requireActiveViolationType(request.getType());

        violation.setParticipant(participant);
        violation.setOwner(participant.getOwner());
        violation.setHorse(participant.getHorse());
        violation.setJockey(participant.getJockey());
        violation.setType(type.getCode());
        violation.setTypeLabel(type.getLabel());
        violation.setSeverity(request.getSeverity());
        violation.setDescription(request.getDescription().trim());
        violation.setPenaltyText(trimToNull(request.getPenaltyText()));
        violation.setOccurredAt(request.getOccurredAt());
        violation.setResultAction(rule.getResultAction());
        violation.setTimePenaltyMillis(defaultLong(rule.getTimePenaltyMillis()));
        if (evidence != null && !evidence.isEmpty()) {
            violation.setEvidenceUrl(cloudinaryUploadService.upload(evidence, RACE_VIOLATION_EVIDENCE_FOLDER));
            violation.setEvidenceName(safeEvidenceName(evidence));
            violation.setEvidenceType(safeEvidenceType(evidence));
            violation.setEvidenceSize(evidence.getSize());
        }
        return mapViolation(raceViolationRepository.save(violation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceViolationResponse> getAdminRaceViolations(Long adminId, Long raceId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view race violations");
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race", "id", raceId);
        }
        return raceViolationRepository.findByRaceIdOrderByOccurredAtDesc(raceId).stream()
                .map(this::mapViolation)
                .toList();
    }

    @Override
    @Transactional
    public RaceComplaintResponse createRaceComplaint(Long ownerId, Long raceId, RaceComplaintRequest request,
                                                     MultipartFile evidence) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can create race complaints");
        if (request == null || request.getAccusedParticipantId() == null) {
            throw new BadRequestException("Accused participant id is required");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Complaint reason is required");
        }
        Race race = requireRace(raceId);
        if (race.getStatus() != RaceStatus.RESULT_CONFIRMED || race.getResultFinalizedAt() == null) {
            throw new BadRequestException("Complaints can only be created after race result is confirmed");
        }
        if (race.getResultFinalizedAt().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Race complaint window has expired");
        }
        RaceParticipant accusedParticipant = raceParticipantRepository.findById(request.getAccusedParticipantId())
                .orElseThrow(() -> new ResourceNotFoundException("RaceParticipant", "id",
                        request.getAccusedParticipantId()));
        if (!accusedParticipant.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Accused participant does not belong to this race");
        }
        boolean ownerInRace = raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId).stream()
                .anyMatch(participant -> participant.getOwner().getId().equals(ownerId));
        if (!ownerInRace) {
            throw new UnauthorizedException("Only owners in this race can create complaints");
        }
        if (accusedParticipant.getOwner().getId().equals(ownerId)) {
            throw new BadRequestException("Owner cannot complain about their own participant");
        }
        String evidenceUrl = evidence == null || evidence.isEmpty()
                ? null
                : cloudinaryUploadService.uploadImage(evidence, RACE_COMPLAINT_EVIDENCE_FOLDER);
        RaceComplaint complaint = RaceComplaint.builder()
                .race(race)
                .complainantOwner(owner)
                .accusedOwner(accusedParticipant.getOwner())
                .accusedParticipant(accusedParticipant)
                .reason(request.getReason())
                .evidenceUrl(evidenceUrl)
                .build();
        RaceComplaint saved = raceComplaintRepository.save(complaint);
        mailService.sendRaceComplaintCreated(saved);
        return mapComplaint(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceComplaintResponse> getOwnerRaceComplaints(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view race complaints");
        return raceComplaintRepository.findByComplainantOwnerIdOrAccusedOwnerIdOrderByCreatedAtDesc(ownerId, ownerId)
                .stream()
                .map(complaint -> mapComplaint(complaint, complaint.getComplainantOwner().getId().equals(ownerId)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceComplaintResponse> getAdminRaceComplaints(Long adminId, RaceComplaintStatus status) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view race complaints");
        List<RaceComplaint> complaints = status == null
                ? raceComplaintRepository.findAllByOrderByCreatedAtDesc()
                : raceComplaintRepository.findByStatusOrderByCreatedAtDesc(status);
        return complaints.stream()
                .map(complaint -> mapComplaint(complaint, true))
                .toList();
    }

    @Override
    @Transactional
    public RaceComplaintResponse resolveRaceComplaint(Long adminId, Long complaintId,
                                                      RaceComplaintResolveRequest request) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can resolve race complaints");
        if (request == null || request.getStatus() == null) {
            throw new BadRequestException("Complaint resolution status is required");
        }
        RaceComplaint complaint = raceComplaintRepository.findById(complaintId)
                .orElseThrow(() -> new ResourceNotFoundException("RaceComplaint", "id", complaintId));
        if (complaint.getStatus() != RaceComplaintStatus.PENDING) {
            throw new BadRequestException("Only pending complaints can be resolved");
        }
        if (request.getStatus() == RaceComplaintStatus.REJECTED) {
            complaint.setStatus(RaceComplaintStatus.REJECTED);
            complaint.setAdminNote(request.getAdminNote());
            complaint.setResolvedAt(LocalDateTime.now());
            complaint.setResolvedBy(adminId);
            return mapComplaint(raceComplaintRepository.save(complaint), true);
        }
        if (request.getStatus() != RaceComplaintStatus.APPROVED) {
            throw new BadRequestException("Complaint can only be approved or rejected");
        }
        approveComplaint(adminId, complaint, request);
        return mapComplaint(raceComplaintRepository.save(complaint), true);
    }

    @Override
    @Transactional
    public List<JockeyChallengeStandingResponse> finalizeJockeyChallenge(Long adminId, Long tournamentId) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can finalize jockey challenge");
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        if (!Boolean.TRUE.equals(tournament.getJockeyChallengeEnabled())) {
            throw new BadRequestException("Jockey challenge is not enabled");
        }
        if (jockeyChallengeResultRepository.existsByTournamentId(tournamentId)) {
            return getJockeyChallengeStandings(tournamentId);
        }
        List<Race> unfinished = raceRepository.findByTournamentIdAndStatusIn(tournamentId,
                List.of(RaceStatus.DRAFT, RaceStatus.PUBLISHED, RaceStatus.OPEN_REGISTRATION,
                        RaceStatus.REGISTRATION_CLOSED, RaceStatus.SCHEDULED, RaceStatus.ONGOING));
        if (!unfinished.isEmpty()) {
            throw new BadRequestException("All races must be confirmed or cancelled before finalizing challenge");
        }
        List<JockeyStanding> standings = calculateStandings(tournamentId);
        LocalDateTime now = LocalDateTime.now();
        List<JockeyChallengeResult> saved = standings.stream()
                .map(standing -> {
                    JockeyChallengeResult result = JockeyChallengeResult.builder()
                            .tournament(tournament)
                            .jockey(standing.jockey())
                            .totalPoints(standing.totalPoints())
                            .firstPlaces(standing.firstPlaces())
                            .secondPlaces(standing.secondPlaces())
                            .thirdPlaces(standing.thirdPlaces())
                            .challengeRank(standing.rank())
                            .prizeAmount(standing.prizeAmount())
                            .payoutStatus(standing.prizeAmount().compareTo(BigDecimal.ZERO) > 0
                                    ? RacePayoutStatus.PENDING
                                    : RacePayoutStatus.NOT_ELIGIBLE)
                            .finalizedBy(adminId)
                            .finalizedAt(now)
                            .build();
                    payoutChallengePrize(result);
                    return result;
                })
                .toList();
        jockeyChallengeResultRepository.saveAll(saved);
        tournament.setJockeyChallengeFinalizedAt(now);
        tournament.setJockeyChallengeFinalizedBy(adminId);
        tournamentRepository.save(tournament);
        return saved.stream().map(this::mapChallengeResult).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyChallengeStandingResponse> getJockeyChallengeStandings(Long tournamentId) {
        if (jockeyChallengeResultRepository.existsByTournamentId(tournamentId)) {
            return jockeyChallengeResultRepository.findByTournamentIdOrderByChallengeRankAsc(tournamentId).stream()
                    .map(this::mapChallengeResult)
                    .toList();
        }
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        return calculateStandings(tournamentId).stream()
                .map(standing -> JockeyChallengeStandingResponse.builder()
                        .jockeyId(standing.jockey().getId())
                        .jockeyUsername(standing.jockey().getUsername())
                        .totalPoints(standing.totalPoints())
                        .firstPlaces(standing.firstPlaces())
                        .secondPlaces(standing.secondPlaces())
                        .thirdPlaces(standing.thirdPlaces())
                        .challengeRank(standing.rank())
                        .prizeAmount(standing.prizeAmount())
                        .payoutStatus(standing.prizeAmount().compareTo(BigDecimal.ZERO) > 0
                                ? RacePayoutStatus.PENDING
                                : RacePayoutStatus.NOT_ELIGIBLE)
                        .build())
                .toList();
    }

    private void validateEligibleInvitation(Long ownerId, Long raceId, Long horseId, JockeyInvitation invitation) {
        if (horseId == null) {
            throw new BadRequestException("Horse id is required");
        }
        if (!invitation.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Owner does not own this jockey invitation");
        }
        if (!invitation.getHorse().getId().equals(horseId)) {
            throw new BadRequestException("Jockey invitation does not belong to the selected horse");
        }
        if (invitation.getRace() != null && !invitation.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Jockey invitation does not belong to this race");
        }
        if (!invitation.getHorse().getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Owner does not own this horse");
        }
        if (invitation.getStatus() != AssignmentStatus.ACCEPTED) {
            throw new BadRequestException("Jockey invitation must be accepted");
        }
        if (invitation.getHorse().getStatus() != HorseStatus.APPROVED) {
            throw new BadRequestException("Horse must be approved");
        }
        if (invitation.getJockeyProfile().getStatus() != JockeyStatus.APPROVED) {
            throw new BadRequestException("Jockey profile must be approved");
        }
    }

    private void approveComplaint(Long adminId, RaceComplaint complaint, RaceComplaintResolveRequest request) {
        BigDecimal fineAmount = defaultZero(request.getFineAmount());
        BigDecimal ownerPrizeReturnAmount = raceResultRepository.findByParticipantId(
                        complaint.getAccusedParticipant().getId())
                .map(this::ownerRacePrizeAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal totalPenalty = ownerPrizeReturnAmount.add(fineAmount);

        complaint.setStatus(RaceComplaintStatus.APPROVED);
        complaint.setAdminNote(request.getAdminNote());
        complaint.setOwnerPrizeReturnAmount(ownerPrizeReturnAmount);
        complaint.setFineAmount(fineAmount);
        complaint.setTotalPenaltyAmount(totalPenalty);
        complaint.setBanUntil(request.getBanUntil());
        complaint.setResolvedAt(LocalDateTime.now());
        complaint.setResolvedBy(adminId);

        User accusedOwner = complaint.getAccusedOwner();
        accusedOwner.setOwnerBanUntil(request.getBanUntil());
        accusedOwner.setOwnerBanReason("Race complaint approved: " + complaint.getId());
        userRepository.save(accusedOwner);

        if (ownerPrizeReturnAmount.compareTo(BigDecimal.ZERO) > 0) {
            String key = "race-complaint:%d:owner-prize-return".formatted(complaint.getId());
            walletService.debitAllowNegative(accusedOwner.getId(), ownerPrizeReturnAmount,
                    WalletTransactionType.ADJUSTMENT, RACE_COMPLAINT_REF, String.valueOf(complaint.getId()),
                    key, "participantId=" + complaint.getAccusedParticipant().getId(),
                    "Race complaint owner prize return");
            walletService.creditAdmin(ownerPrizeReturnAmount, WalletTransactionType.ADJUSTMENT,
                    RACE_COMPLAINT_REF, String.valueOf(complaint.getId()),
                    "race-complaint:%d:admin-owner-prize-return".formatted(complaint.getId()),
                    "participantId=" + complaint.getAccusedParticipant().getId(),
                    "Race complaint owner prize returned");
            complaint.setOwnerPrizeReturnDebitKey(key);
        }
        if (fineAmount.compareTo(BigDecimal.ZERO) > 0) {
            String key = "race-complaint:%d:fine".formatted(complaint.getId());
            walletService.debitAllowNegative(accusedOwner.getId(), fineAmount,
                    WalletTransactionType.ADJUSTMENT, RACE_COMPLAINT_REF, String.valueOf(complaint.getId()),
                    key, "participantId=" + complaint.getAccusedParticipant().getId(),
                    "Race complaint fine");
            walletService.creditAdmin(fineAmount, WalletTransactionType.ADJUSTMENT,
                    RACE_COMPLAINT_REF, String.valueOf(complaint.getId()),
                    "race-complaint:%d:admin-fine".formatted(complaint.getId()),
                    "participantId=" + complaint.getAccusedParticipant().getId(),
                    "Race complaint fine received");
            complaint.setFineDebitKey(key);
        }
    }

    private Race requireAssignedRefereeRace(Long refereeId, Long raceId) {
        User referee = requireUser(refereeId);
        requireRole(referee, UserRole.REFEREE, "Only referees can operate assigned races");
        Race race = requireRace(raceId);
        if (race.getReferee() == null || !race.getReferee().getId().equals(refereeId)) {
            throw new UnauthorizedException("Referee is not assigned to this race");
        }
        return race;
    }

    private void validateRaceCanAcceptViolation(Race race) {
        if (race.getStatus() != RaceStatus.SCHEDULED && race.getStatus() != RaceStatus.ONGOING) {
            throw new BadRequestException("Race violations can only be recorded before result confirmation");
        }
        if (race.getStatus() == RaceStatus.RESULT_CONFIRMED || raceResultRepository.existsByRaceId(race.getId())) {
            throw new BadRequestException("Cannot update race violations after result confirmation");
        }
    }

    private RaceParticipant requireRaceViolationParticipant(Long raceId, RaceViolationRequest request) {
        if (request == null || request.getParticipantId() == null) {
            throw new BadRequestException("Violation participant is required");
        }
        if (request.getType() == null || request.getType().isBlank()) {
            throw new BadRequestException("Violation type is required");
        }
        if (request.getSeverity() == null) {
            throw new BadRequestException("Violation severity is required");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BadRequestException("Violation description is required");
        }
        if (request.getOccurredAt() == null) {
            throw new BadRequestException("Violation occurred time is required");
        }
        RaceParticipant participant = raceParticipantRepository.findById(request.getParticipantId())
                .orElseThrow(() -> new ResourceNotFoundException("RaceParticipant", "id", request.getParticipantId()));
        if (!participant.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Violation participant does not belong to this race");
        }
        return participant;
    }

    private ViolationPenaltyRuleResponse ruleForSeverity(RaceViolationSeverity severity) {
        return systemSettingsService.getViolationPenaltyRules().stream()
                .filter(rule -> rule.getSeverity() == severity)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Violation penalty rule is not configured for " + severity));
    }

    private void validateViolationEvidence(MultipartFile evidence, boolean required) {
        if (evidence == null || evidence.isEmpty()) {
            if (required) {
                throw new BadRequestException("Violation evidence is required");
            }
            return;
        }
        if (evidence.getSize() > MAX_VIOLATION_EVIDENCE_BYTES) {
            throw new BadRequestException("Violation evidence must not exceed 100MB");
        }
        String contentType = safeEvidenceType(evidence).toLowerCase(Locale.ROOT);
        String filename = safeEvidenceName(evidence).toLowerCase(Locale.ROOT);
        boolean allowedByType = List.of(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/gif",
                "video/mp4",
                "video/quicktime"
        ).contains(contentType);
        boolean allowedByName = filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".png")
                || filename.endsWith(".webp")
                || filename.endsWith(".gif")
                || filename.endsWith(".mp4")
                || filename.endsWith(".mov");
        if (!allowedByType && !allowedByName) {
            throw new BadRequestException("Only JPG, PNG, WEBP, GIF, MP4, or MOV evidence files are allowed");
        }
    }

    private String safeEvidenceName(MultipartFile evidence) {
        String filename = evidence == null ? null : evidence.getOriginalFilename();
        return filename == null || filename.isBlank() ? "evidence" : filename;
    }

    private String safeEvidenceType(MultipartFile evidence) {
        String contentType = evidence == null ? null : evidence.getContentType();
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private boolean canCancelRace(RaceStatus status) {
        return status == RaceStatus.DRAFT
                || status == RaceStatus.PUBLISHED
                || status == RaceStatus.OPEN_REGISTRATION
                || status == RaceStatus.REGISTRATION_CLOSED
                || status == RaceStatus.SCHEDULED;
    }

    private void validateRaceReadyForSchedule(Race race) {
        if (race.getStatus() == RaceStatus.RESULT_CONFIRMED || race.getStatus() == RaceStatus.CANCELLED) {
            throw new BadRequestException("Completed or cancelled races cannot be scheduled");
        }
        List<RaceParticipant> participants = raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId());
        if (participants.size() > race.getMaxParticipants()) {
            throw new BadRequestException("Race exceeds maximum participant capacity");
        }
        if (race.getReferee() != null) {
            validateRefereeAvailability(race, race.getReferee().getId());
        }
    }

    private void validateRaceGatesReady(List<RaceParticipant> participants) {
        Set<Integer> gates = new HashSet<>();
        for (RaceParticipant participant : participants) {
            if (participant.getStatus() != RaceParticipantStatus.CHECKED_IN) {
                continue;
            }
            Integer gateNumber = participant.getGateNumber();
            if (gateNumber == null || gateNumber <= 0) {
                throw new BadRequestException("Gate number must be assigned before race starts");
            }
            if (!gates.add(gateNumber)) {
                throw new BadRequestException("Gate number already exists in this race");
            }
        }
    }

    private void validateRefereeAvailability(Race race, Long refereeId) {
        if (raceRepository.existsRefereeOverlapExcludingRace(refereeId, race.getId(),
                race.getScheduledStartAt(), race.getScheduledEndAt())) {
            throw new BadRequestException("Referee cannot be assigned to overlapping races");
        }
    }

    private void validateJockeyScheduleAcrossTournament(List<Race> races) {
        Map<Long, List<Race>> racesByJockey = new HashMap<>();
        for (Race race : races) {
            for (RaceParticipant participant : raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId())) {
                Long jockeyId = participant.getJockey().getId();
                for (Race existingRace : racesByJockey.getOrDefault(jockeyId, List.of())) {
                    if (overlaps(existingRace, race)) {
                        throw new BadRequestException("Jockey cannot join overlapping races");
                    }
                }
                racesByJockey.computeIfAbsent(jockeyId, ignored -> new ArrayList<>()).add(race);
            }
        }
    }

    private boolean overlaps(Race first, Race second) {
        return first.getScheduledStartAt().isBefore(second.getScheduledEndAt())
                && first.getScheduledEndAt().isAfter(second.getScheduledStartAt());
    }

    private void sendRaceScheduledEmails(Race race) {
        recipientsFor(race).forEach(recipient -> safeSendMail(
                () -> mailService.sendRaceScheduled(race, recipient),
                "race scheduled", race.getId(), recipient.getId()));
    }

    private void notifyRegistrationCreated(RaceRegistration registration) {
        notifyUser(registration.getOwner(), NotificationType.REGISTRATION_CREATED,
                "Race registration submitted",
                "Your registration for race " + registration.getRace().getName() + " was submitted",
                RACE_REGISTRATION_REF, String.valueOf(registration.getId()), registrationMetadata(registration));
        safeSendMail(() -> mailService.sendRegistrationCreated(registration.getOwner(),
                        registration.getRace().getName(), RACE_REGISTRATION_REF, String.valueOf(registration.getId())),
                "registration created", registration.getId(), registration.getOwner().getId());
        userRepository.findByRole(UserRole.ADMIN).forEach(admin -> notifyUser(admin,
                NotificationType.REGISTRATION_CREATED, "Race registration submitted",
                registration.getOwner().getUsername() + " submitted a registration for " + registration.getRace().getName(),
                RACE_REGISTRATION_REF, String.valueOf(registration.getId()), registrationMetadata(registration)));
    }

    private void notifyRegistrationStatus(RaceRegistration registration, NotificationType type, String title,
                                          String message, boolean sendEmail) {
        notifyUser(registration.getOwner(), type, title, message,
                RACE_REGISTRATION_REF, String.valueOf(registration.getId()), registrationMetadata(registration));
        if (sendEmail && type == NotificationType.REGISTRATION_APPROVED) {
            safeSendMail(() -> mailService.sendRegistrationApproved(registration.getOwner(),
                            registration.getRace().getName(), RACE_REGISTRATION_REF,
                            String.valueOf(registration.getId())),
                    "registration approved", registration.getId(), registration.getOwner().getId());
        } else if (sendEmail && type == NotificationType.REGISTRATION_REJECTED) {
            safeSendMail(() -> mailService.sendRegistrationRejected(registration.getOwner(),
                            registration.getRace().getName(), RACE_REGISTRATION_REF,
                            String.valueOf(registration.getId())),
                    "registration rejected", registration.getId(), registration.getOwner().getId());
        }
    }

    private void publishRaceScheduled(Race race) {
        notifyRaceEvent(race, NotificationType.RACE_SCHEDULED, "Race scheduled",
                "Race " + race.getName() + " has been scheduled");
        publishRaceStatus(race, "RACE_SCHEDULED");
    }

    private void notifyRaceRefereeAssigned(Race race, User referee) {
        notifyRaceEvent(race, NotificationType.RACE_REFEREE_ASSIGNED, "Race referee assigned",
                "A referee was assigned to race " + race.getName());
        notifyUser(referee, NotificationType.RACE_REFEREE_ASSIGNED, "Race referee assigned",
                "You were assigned to race " + race.getName(),
                "RACE", String.valueOf(race.getId()), raceMetadata(race));
    }

    private void notifyRaceEvent(Race race, NotificationType type, String title, String message) {
        recipientsFor(race).forEach(recipient -> notifyUser(recipient, type, title, message,
                "RACE", String.valueOf(race.getId()), raceMetadata(race)));
    }

    private void notifyParticipantCheckIn(RaceParticipant participant) {
        String status = participant.getStatus() == RaceParticipantStatus.ABSENT
                ? "marked absent"
                : "check-in status updated to " + participant.getStatus();
        String message = "Participant " + participant.getHorse().getName() + " was " + status;
        notifyUser(participant.getOwner(), NotificationType.RACE_CHECK_IN_UPDATED, "Race check-in updated",
                message, "RACE_PARTICIPANT", String.valueOf(participant.getId()), participantMetadata(participant));
        notifyUser(participant.getJockey(), NotificationType.RACE_CHECK_IN_UPDATED, "Race check-in updated",
                message, "RACE_PARTICIPANT", String.valueOf(participant.getId()), participantMetadata(participant));
    }

    private void publishRaceResults(Race race) {
        if (realtimeEventService == null) {
            return;
        }
        try {
            realtimeEventService.publishRaceResult(race, "RACE_RESULT_PUBLISHED", String.valueOf(race.getId()));
            realtimeEventService.publishTournamentLeaderboard(race.getTournament().getId(),
                    "TOURNAMENT_LEADERBOARD_UPDATED", String.valueOf(race.getId()));
        } catch (RuntimeException ex) {
            log.warn("Could not publish race result websocket event: raceId={}", race.getId(), ex);
        }
    }

    private void publishRaceStatus(Race race, String eventType) {
        if (realtimeEventService == null) {
            return;
        }
        try {
            realtimeEventService.publishRaceStatus(race, eventType, race.getStatus().name(), String.valueOf(race.getId()));
        } catch (RuntimeException ex) {
            log.warn("Could not publish race status websocket event: raceId={}, eventType={}",
                    race.getId(), eventType, ex);
        }
    }

    private void notifyRaceResults(Race race, List<RaceResult> results) {
        recipientsFor(race).forEach(recipient -> {
            notifyUser(recipient, NotificationType.RACE_RESULT_PUBLISHED, "Race result published",
                    "Race result was confirmed for " + race.getName(),
                    RACE_RESULT_REF, String.valueOf(race.getId()), raceMetadata(race));
            safeSendMail(() -> mailService.sendRaceResultPublished(race, recipient, RACE_RESULT_REF,
                            String.valueOf(race.getId())),
                    "race result", race.getId(), recipient.getId());
        });
    }

    private void notifyRacePrizePayout(RaceResult result, boolean paid) {
        NotificationType type = paid ? NotificationType.PRIZE_PAYOUT_PAID : NotificationType.PRIZE_PAYOUT_UNPAID;
        String status = paid ? "paid" : "unpaid";
        String subject = paid ? "Race prize paid" : "Race prize unpaid";
        String baseMessage = "Race prize payout is " + status + " for race " + result.getRace().getName();
        if (defaultZero(result.getOwnerPrizeAmount()).compareTo(BigDecimal.ZERO) > 0) {
            notifyPrizeRecipient(result.getOwner(), type, subject, baseMessage,
                    RACE_RESULT_REF, String.valueOf(result.getId()));
        }
        if (defaultZero(result.getJockeyPrizeAmount()).compareTo(BigDecimal.ZERO) > 0) {
            notifyPrizeRecipient(result.getJockey(), type, subject, baseMessage,
                    RACE_RESULT_REF, String.valueOf(result.getId()));
        }
    }

    private void notifyChallengePrizePayout(JockeyChallengeResult result, boolean paid) {
        NotificationType type = paid ? NotificationType.PRIZE_PAYOUT_PAID : NotificationType.PRIZE_PAYOUT_UNPAID;
        String status = paid ? "paid" : "unpaid";
        String subject = paid ? "Jockey challenge prize paid" : "Jockey challenge prize unpaid";
        String referenceId = "%d:%d".formatted(result.getTournament().getId(), result.getJockey().getId());
        notifyPrizeRecipient(result.getJockey(), type, subject,
                "Jockey challenge prize payout is " + status,
                JOCKEY_CHALLENGE_REF, referenceId);
    }

    private void notifyPrizeRecipient(User recipient, NotificationType type, String title, String message,
                                      String referenceType, String referenceId) {
        notifyUser(recipient, type, title, message, referenceType, referenceId,
                "{\"status\":\"%s\"}".formatted(type == NotificationType.PRIZE_PAYOUT_PAID ? "PAID" : "UNPAID"));
        safeSendMail(() -> mailService.sendPrizePayout(recipient, title, message, referenceType, referenceId),
                "prize payout", Long.valueOf(referenceId.replaceAll(":.*", "")), recipient.getId());
    }

    private void notifyUser(User recipient, NotificationType type, String title, String message,
                            String referenceType, String referenceId, String metadataJson) {
        if (notificationService == null) {
            return;
        }
        try {
            notificationService.notify(recipient, type, title, message, referenceType, referenceId, metadataJson);
        } catch (RuntimeException ex) {
            log.warn("Could not create notification: recipientId={}, type={}, referenceType={}, referenceId={}",
                    recipient == null ? null : recipient.getId(), type, referenceType, referenceId, ex);
        }
    }

    private void safeSendMail(Runnable action, String event, Long referenceId, Long recipientId) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Could not send email: event={}, referenceId={}, recipientId={}",
                    event, referenceId, recipientId, ex);
        }
    }

    private Set<User> recipientsFor(Race race) {
        Set<User> recipients = new LinkedHashSet<>();
        raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId()).forEach(participant -> {
            recipients.add(participant.getOwner());
            recipients.add(participant.getJockey());
        });
        recipients.add(race.getReferee());
        recipients.remove(null);
        return recipients;
    }

    private String registrationMetadata(RaceRegistration registration) {
        return "{\"raceId\":%d,\"tournamentId\":%d,\"horseId\":%d,\"jockeyId\":%d,\"status\":\"%s\"}".formatted(
                registration.getRace().getId(), registration.getRace().getTournament().getId(),
                registration.getHorse().getId(), registration.getJockey().getId(), registration.getStatus());
    }

    private String raceMetadata(Race race) {
        return "{\"raceId\":%d,\"tournamentId\":%d,\"status\":\"%s\"}".formatted(
                race.getId(), race.getTournament().getId(), race.getStatus());
    }

    private String participantMetadata(RaceParticipant participant) {
        return "{\"raceId\":%d,\"participantId\":%d,\"horseId\":%d,\"jockeyId\":%d,\"status\":\"%s\"}".formatted(
                participant.getRace().getId(), participant.getId(), participant.getHorse().getId(),
                participant.getJockey().getId(), participant.getStatus());
    }

    private void debitRegistrationFee(RaceRegistration registration) {
        BigDecimal entryFee = defaultZero(registration.getEntryFeeAmount());
        if (entryFee.compareTo(BigDecimal.ZERO) > 0) {
            String key = "race-registration:%d:entry-debit".formatted(registration.getId());
            walletService.debit(registration.getOwner().getId(), entryFee, WalletTransactionType.ENTRY_FEE,
                    RACE_REGISTRATION_REF, String.valueOf(registration.getId()), key, null, "Race entry fee paid");
            walletService.creditAdmin(entryFee, WalletTransactionType.ENTRY_FEE,
                    RACE_REGISTRATION_REF, String.valueOf(registration.getId()),
                    "race-registration:%d:entry-admin-credit".formatted(registration.getId()),
                    null, "Race entry fee received");
            registration.setEntryFeeDebitKey(key);
        }
        raceRegistrationRepository.save(registration);
    }

    private void refundRegistrationFee(RaceRegistration registration, String note) {
        BigDecimal entryFee = defaultZero(registration.getEntryFeeAmount());
        if (entryFee.compareTo(BigDecimal.ZERO) > 0) {
            String key = "race-registration:%d:entry-refund".formatted(registration.getId());
            walletService.debitAdmin(entryFee, WalletTransactionType.REFUND,
                    RACE_REGISTRATION_REF, String.valueOf(registration.getId()),
                    "race-registration:%d:entry-admin-refund".formatted(registration.getId()),
                    null, note);
            walletService.refund(registration.getOwner().getId(), entryFee,
                    RACE_REGISTRATION_REF, String.valueOf(registration.getId()), key, null, note);
            registration.setEntryFeeRefundKey(key);
        }
    }

    private void validateResultEntries(List<RaceResultEntryRequest> entries, Map<Long, RaceParticipant> participantById) {
        Set<Long> participantIds = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (RaceResultEntryRequest entry : entries) {
            RaceParticipant participant = participantById.get(entry.getParticipantId());
            if (participant == null) {
                throw new BadRequestException("Result participant does not belong to this race");
            }
            if (!participantIds.add(entry.getParticipantId())) {
                throw new BadRequestException("Duplicate participant in race result");
            }
            if (entry.getStatus() == null) {
                throw new BadRequestException("Result status is required");
            }
            if (entry.getStatus() == RaceParticipantStatus.FINISHED) {
                if (participant.getStatus() != RaceParticipantStatus.CHECKED_IN) {
                    throw new BadRequestException("Only checked-in participants can finish a race");
                }
                if (entry.getRank() == null || entry.getRank() <= 0) {
                    throw new BadRequestException("Finished participants must have a positive rank");
                }
                if (!ranks.add(entry.getRank())) {
                    throw new BadRequestException("Result rank must be unique within a race");
                }
            } else if (entry.getRank() != null) {
                throw new BadRequestException("Only finished participants can have a rank");
            }
        }
        if (participantIds.size() != participantById.size()) {
            throw new BadRequestException("Race result must include every approved participant");
        }
    }

    private List<RaceResultEntryRequest> applyViolationResultActions(List<RaceResultEntryRequest> entries,
                                                                     List<RaceViolation> violations) {
        if (violations == null || violations.isEmpty()) {
            return entries;
        }
        Map<Long, List<RaceViolation>> byParticipantId = violations.stream()
                .collect(Collectors.groupingBy(violation -> violation.getParticipant().getId()));
        for (RaceResultEntryRequest entry : entries) {
            List<RaceViolation> participantViolations = byParticipantId.get(entry.getParticipantId());
            if (participantViolations == null || participantViolations.isEmpty()) {
                continue;
            }
            boolean disqualified = participantViolations.stream()
                    .anyMatch(violation -> violation.getResultAction() == ViolationResultAction.DISQUALIFY);
            long timePenaltyMillis = participantViolations.stream()
                    .filter(violation -> violation.getResultAction() == ViolationResultAction.TIME_PENALTY)
                    .mapToLong(violation -> defaultLong(violation.getTimePenaltyMillis()))
                    .filter(value -> value > 0)
                    .sum();
            if (disqualified) {
                entry.setStatus(RaceParticipantStatus.DISQUALIFIED);
                entry.setRank(null);
                entry.setFinishTimeMillis(0L);
                entry.setNote(appendResultNote(entry.getNote(), "DQ by violations "
                        + violationIds(participantViolations)));
            } else if (timePenaltyMillis > 0 && entry.getStatus() == RaceParticipantStatus.FINISHED) {
                entry.setFinishTimeMillis(defaultLong(entry.getFinishTimeMillis()) + timePenaltyMillis);
                entry.setNote(appendResultNote(entry.getNote(), "Penalty +"
                        + timePenaltyMillis + "ms by violations " + violationIds(participantViolations)));
            }
        }
        rerankFinishedEntries(entries);
        return entries;
    }

    private void rerankFinishedEntries(List<RaceResultEntryRequest> entries) {
        List<RaceResultEntryRequest> finished = entries.stream()
                .filter(entry -> entry.getStatus() == RaceParticipantStatus.FINISHED)
                .sorted(Comparator
                        .comparing((RaceResultEntryRequest entry) -> defaultLong(entry.getFinishTimeMillis()))
                        .thenComparing(RaceResultEntryRequest::getParticipantId))
                .toList();
        for (int index = 0; index < finished.size(); index++) {
            finished.get(index).setRank(index + 1);
        }
        entries.stream()
                .filter(entry -> entry.getStatus() != RaceParticipantStatus.FINISHED)
                .forEach(entry -> entry.setRank(null));
    }

    private String violationIds(List<RaceViolation> violations) {
        return violations.stream()
                .map(violation -> "#" + violation.getId())
                .collect(Collectors.joining(", "));
    }

    private String appendResultNote(String current, String addition) {
        String base = current == null || current.isBlank() ? addition : current.trim() + " | " + addition;
        return base.length() <= 1000 ? base : base.substring(0, 1000);
    }

    private void autoMarkRegisteredParticipantsAbsent(List<RaceParticipant> participants, Long refereeId) {
        LocalDateTime now = LocalDateTime.now();
        participants.stream()
                .filter(participant -> participant.getStatus() == RaceParticipantStatus.REGISTERED)
                .forEach(participant -> {
                    participant.setStatus(RaceParticipantStatus.ABSENT);
                    if (participant.getCheckInNote() == null || participant.getCheckInNote().isBlank()) {
                        participant.setCheckInNote("Auto marked absent when race started");
                    }
                    participant.setCheckedInAt(now);
                    participant.setCheckedInBy(refereeId);
                    raceParticipantRepository.save(participant);
                });
    }

    private List<RaceResultEntryRequest> completeResultEntries(List<RaceResultEntryRequest> entries,
                                                              List<RaceParticipant> participants) {
        Map<Long, RaceResultEntryRequest> entryByParticipantId = entries.stream()
                .collect(Collectors.toMap(RaceResultEntryRequest::getParticipantId, Function.identity(),
                        (first, duplicate) -> first));
        List<RaceResultEntryRequest> completed = new ArrayList<>(entries);
        for (RaceParticipant participant : participants) {
            if (entryByParticipantId.containsKey(participant.getId())) {
                continue;
            }
            if (participant.getStatus() == RaceParticipantStatus.ABSENT) {
                RaceResultEntryRequest absentEntry = new RaceResultEntryRequest();
                absentEntry.setParticipantId(participant.getId());
                absentEntry.setStatus(RaceParticipantStatus.ABSENT);
                absentEntry.setNote(participant.getCheckInNote());
                completed.add(absentEntry);
            }
        }
        return completed;
    }

    private RaceResult buildRaceResult(Race race, RaceParticipant participant, RaceResultEntryRequest entry,
                                       Long refereeId, LocalDateTime now) {
        participant.setStatus(entry.getStatus());
        raceParticipantRepository.save(participant);
        BigDecimal prizeAmount = prizeAmountFor(race, entry.getRank());
        RacePayoutStatus payoutStatus = prizeAmount.compareTo(BigDecimal.ZERO) > 0
                ? RacePayoutStatus.PENDING
                : RacePayoutStatus.NOT_ELIGIBLE;
        return RaceResult.builder()
                .race(race)
                .participant(participant)
                .owner(participant.getOwner())
                .horse(participant.getHorse())
                .jockey(participant.getJockey())
                .rank(entry.getRank())
                .finishTimeMillis(entry.getFinishTimeMillis())
                .status(entry.getStatus())
                .jockeyChallengePoints(challengePointsFor(race.getTournament(), entry.getRank(), entry.getStatus()))
                .prizeAmount(prizeAmount)
                .ownerPrizeAmount(prizeAmount)
                .jockeyPrizeAmount(BigDecimal.ZERO)
                .jockeyPrizePercent(BigDecimal.ZERO)
                .payoutStatus(payoutStatus)
                .note(entry.getNote())
                .finalizedBy(refereeId)
                .finalizedAt(now)
                .build();
    }

    private void payoutRacePrize(RaceResult result) {
        BigDecimal prizeAmount = defaultZero(result.getPrizeAmount());
        if (result.getPayoutStatus() != RacePayoutStatus.PENDING
                || prizeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!adminWalletCanPay(prizeAmount)) {
            result.setPayoutStatus(RacePayoutStatus.UNPAID);
            raceResultRepository.save(result);
            notifyRacePrizePayout(result, false);
            return;
        }
        String referenceId = String.valueOf(result.getId());
        walletService.debitAdmin(prizeAmount, WalletTransactionType.PRIZE_PAYOUT,
                RACE_RESULT_REF, referenceId, "race-result:%d:admin-prize-debit".formatted(result.getId()),
                null, "Race prize payout");
        BigDecimal ownerAmount = defaultZero(result.getOwnerPrizeAmount());
        if (ownerAmount.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(result.getOwner().getId(), ownerAmount, WalletTransactionType.PRIZE_PAYOUT,
                    RACE_RESULT_REF, referenceId, "race-result:%d:owner-prize-credit".formatted(result.getId()),
                    null, "Race prize payout owner share");
        }
        BigDecimal jockeyAmount = defaultZero(result.getJockeyPrizeAmount());
        if (jockeyAmount.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(result.getJockey().getId(), jockeyAmount, WalletTransactionType.PRIZE_PAYOUT,
                    RACE_RESULT_REF, referenceId, "race-result:%d:jockey-prize-credit".formatted(result.getId()),
                    null, "Race prize payout jockey share");
        }
        result.setPayoutStatus(RacePayoutStatus.PAID);
        raceResultRepository.save(result);
        notifyRacePrizePayout(result, true);
    }

    private void payoutChallengePrize(JockeyChallengeResult result) {
        if (result.getPayoutStatus() != RacePayoutStatus.PENDING
                || result.getPrizeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!adminWalletCanPay(result.getPrizeAmount())) {
            result.setPayoutStatus(RacePayoutStatus.UNPAID);
            notifyChallengePrizePayout(result, false);
            return;
        }
        String referenceId = "%d:%d".formatted(result.getTournament().getId(), result.getJockey().getId());
        walletService.debitAdmin(result.getPrizeAmount(), WalletTransactionType.PRIZE_PAYOUT,
                JOCKEY_CHALLENGE_REF, referenceId,
                "jockey-challenge:%s:admin-prize-debit".formatted(referenceId),
                null, "Jockey challenge prize payout");
        walletService.credit(result.getJockey().getId(), result.getPrizeAmount(), WalletTransactionType.PRIZE_PAYOUT,
                JOCKEY_CHALLENGE_REF, referenceId,
                "jockey-challenge:%s:jockey-prize-credit".formatted(referenceId),
                null, "Jockey challenge prize payout");
        result.setPayoutStatus(RacePayoutStatus.PAID);
        notifyChallengePrizePayout(result, true);
    }

    private boolean adminWalletCanPay(BigDecimal amount) {
        return walletService.getOrCreateAdminWallet().getAvailableBalance().compareTo(amount) >= 0;
    }

    private List<JockeyStanding> calculateStandings(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        Map<Long, JockeyStandingAccumulator> byJockey = new HashMap<>();
        for (RaceResult result : raceResultRepository.findByRaceTournamentId(tournamentId)) {
            if (result.getStatus() != RaceParticipantStatus.FINISHED
                    || defaultInt(result.getJockeyChallengePoints()) <= 0) {
                continue;
            }
            JockeyStandingAccumulator accumulator = byJockey.computeIfAbsent(result.getJockey().getId(),
                    ignored -> new JockeyStandingAccumulator(result.getJockey()));
            accumulator.totalPoints += result.getJockeyChallengePoints();
            if (Integer.valueOf(1).equals(result.getRank())) {
                accumulator.firstPlaces++;
            } else if (Integer.valueOf(2).equals(result.getRank())) {
                accumulator.secondPlaces++;
            } else if (Integer.valueOf(3).equals(result.getRank())) {
                accumulator.thirdPlaces++;
            }
        }
        List<JockeyStandingAccumulator> sorted = byJockey.values().stream()
                .sorted(Comparator.comparingInt(JockeyStandingAccumulator::totalPoints).reversed()
                        .thenComparing(Comparator.comparingInt(JockeyStandingAccumulator::firstPlaces).reversed())
                        .thenComparing(Comparator.comparingInt(JockeyStandingAccumulator::secondPlaces).reversed())
                        .thenComparing(Comparator.comparingInt(JockeyStandingAccumulator::thirdPlaces).reversed())
                        .thenComparing(item -> item.jockey.getUsername()))
                .toList();
        Map<Integer, BigDecimal> prizeByRank = tournament.getJockeyChallengePrizes().stream()
                .collect(Collectors.toMap(JockeyChallengePrize::getRank, JockeyChallengePrize::getAmount));
        List<JockeyStanding> standings = new ArrayList<>();
        int index = 0;
        while (index < sorted.size()) {
            JockeyStandingAccumulator first = sorted.get(index);
            int rank = index + 1;
            List<JockeyStandingAccumulator> tied = new ArrayList<>();
            tied.add(first);
            int next = index + 1;
            while (next < sorted.size() && first.sameScore(sorted.get(next))) {
                tied.add(sorted.get(next));
                next++;
            }
            BigDecimal groupPrize = defaultZero(prizeByRank.get(rank));
            BigDecimal share = tied.isEmpty() || groupPrize.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : groupPrize.divide(BigDecimal.valueOf(tied.size()), 2, RoundingMode.DOWN);
            for (JockeyStandingAccumulator item : tied) {
                standings.add(new JockeyStanding(item.jockey, item.totalPoints, item.firstPlaces,
                        item.secondPlaces, item.thirdPlaces, rank, share));
            }
            index = next;
        }
        return standings;
    }

    private BigDecimal prizeAmountFor(Race race, Integer rank) {
        if (rank == null) {
            return BigDecimal.ZERO;
        }
        return race.getPrizes().stream()
                .filter(prize -> rank.equals(prize.getRank()))
                .map(RacePrize::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private int challengePointsFor(Tournament tournament, Integer rank, RaceParticipantStatus status) {
        if (!Boolean.TRUE.equals(tournament.getJockeyChallengeEnabled())
                || status != RaceParticipantStatus.FINISHED
                || rank == null) {
            return 0;
        }
        return switch (rank) {
            case 1 -> tournament.getJockeyChallengeFirstPoints();
            case 2 -> tournament.getJockeyChallengeSecondPoints();
            case 3 -> tournament.getJockeyChallengeThirdPoints();
            default -> 0;
        };
    }

    private Race requireRace(Long raceId) {
        return raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
    }

    private RaceRegistration requireRegistration(Long registrationId) {
        return raceRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("RaceRegistration", "id", registrationId));
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

    private List<RaceRegistrationStatus> activeRegistrationStatuses() {
        return List.of(RaceRegistrationStatus.PENDING, RaceRegistrationStatus.APPROVED);
    }

    private void validateOwnerHorseMinimums(Tournament tournament) {
        List<Object[]> ownerCounts = raceRegistrationRepository.countByOwnerForTournament(
                tournament.getId(), activeRegistrationStatuses());
        for (Object[] row : ownerCounts) {
            Long ownerId = (Long) row[0];
            String username = (String) row[1];
            long count = (Long) row[2];
            if (count < tournament.getMinHorsesPerOwner()) {
                throw new BadRequestException("Owner " + username + " (" + ownerId
                        + ") must register at least " + tournament.getMinHorsesPerOwner()
                        + " horses in this tournament");
            }
        }
    }

    private RaceRegistrationResponse mapRegistration(RaceRegistration registration) {
        return RaceRegistrationResponse.builder()
                .id(registration.getId())
                .raceId(registration.getRace().getId())
                .raceName(registration.getRace().getName())
                .tournamentId(registration.getRace().getTournament().getId())
                .ownerId(registration.getOwner().getId())
                .ownerUsername(registration.getOwner().getUsername())
                .horseId(registration.getHorse().getId())
                .horseName(registration.getHorse().getName())
                .jockeyId(registration.getJockey().getId())
                .jockeyUsername(registration.getJockey().getUsername())
                .jockeyInvitationId(registration.getJockeyInvitation().getId())
                .status(registration.getStatus())
                .entryFeeAmount(registration.getEntryFeeAmount())
                .ownerNote(registration.getOwnerNote())
                .reviewNote(registration.getReviewNote())
                .withdrawNote(registration.getWithdrawNote())
                .reviewedBy(registration.getReviewedBy())
                .reviewedAt(registration.getReviewedAt())
                .createdAt(registration.getCreatedAt())
                .updatedAt(registration.getUpdatedAt())
                .build();
    }

    private RaceParticipantResponse mapParticipant(RaceParticipant participant) {
        return RaceParticipantResponse.builder()
                .id(participant.getId())
                .raceId(participant.getRace().getId())
                .registrationId(participant.getRegistration().getId())
                .ownerId(participant.getOwner().getId())
                .ownerUsername(participant.getOwner().getUsername())
                .horseId(participant.getHorse().getId())
                .horseName(participant.getHorse().getName())
                .jockeyId(participant.getJockey().getId())
                .jockeyUsername(participant.getJockey().getUsername())
                .gateNumber(participant.getGateNumber())
                .status(participant.getStatus())
                .checkInNote(participant.getCheckInNote())
                .checkedInAt(participant.getCheckedInAt())
                .checkedInBy(participant.getCheckedInBy())
                .createdAt(participant.getCreatedAt())
                .build();
    }

    private RaceComplaintResponse mapComplaint(RaceComplaint complaint, boolean revealComplainant) {
        RaceParticipant accused = complaint.getAccusedParticipant();
        return RaceComplaintResponse.builder()
                .id(complaint.getId())
                .raceId(complaint.getRace().getId())
                .raceName(complaint.getRace().getName())
                .complainantOwnerId(revealComplainant ? complaint.getComplainantOwner().getId() : null)
                .accusedOwnerId(complaint.getAccusedOwner().getId())
                .accusedOwnerUsername(complaint.getAccusedOwner().getUsername())
                .accusedParticipantId(accused.getId())
                .accusedHorseId(accused.getHorse().getId())
                .accusedHorseName(accused.getHorse().getName())
                .status(complaint.getStatus())
                .reason(complaint.getReason())
                .evidenceUrl(complaint.getEvidenceUrl())
                .adminNote(complaint.getAdminNote())
                .ownerPrizeReturnAmount(defaultZero(complaint.getOwnerPrizeReturnAmount()))
                .fineAmount(defaultZero(complaint.getFineAmount()))
                .totalPenaltyAmount(defaultZero(complaint.getTotalPenaltyAmount()))
                .banUntil(complaint.getBanUntil())
                .createdAt(complaint.getCreatedAt())
                .resolvedAt(complaint.getResolvedAt())
                .resolvedBy(complaint.getResolvedBy())
                .build();
    }

    private RaceViolationResponse mapViolation(RaceViolation violation) {
        return RaceViolationResponse.builder()
                .id(violation.getId())
                .raceId(violation.getRace().getId())
                .raceName(violation.getRace().getName())
                .participantId(violation.getParticipant().getId())
                .gateNumber(violation.getParticipant().getGateNumber())
                .ownerId(violation.getOwner().getId())
                .ownerUsername(violation.getOwner().getUsername())
                .horseId(violation.getHorse().getId())
                .horseName(violation.getHorse().getName())
                .jockeyId(violation.getJockey().getId())
                .jockeyUsername(violation.getJockey().getUsername())
                .refereeId(violation.getReferee().getId())
                .refereeUsername(violation.getReferee().getUsername())
                .type(violation.getType())
                .typeLabel(resolveViolationTypeLabel(violation))
                .severity(violation.getSeverity())
                .description(violation.getDescription())
                .penaltyText(violation.getPenaltyText())
                .occurredAt(violation.getOccurredAt())
                .resultAction(violation.getResultAction())
                .timePenaltyMillis(defaultLong(violation.getTimePenaltyMillis()))
                .evidenceUrl(violation.getEvidenceUrl())
                .evidenceName(violation.getEvidenceName())
                .evidenceType(violation.getEvidenceType())
                .evidenceSize(violation.getEvidenceSize())
                .createdAt(violation.getCreatedAt())
                .updatedAt(violation.getUpdatedAt())
                .build();
    }

    private String resolveViolationTypeLabel(RaceViolation violation) {
        if (violation.getTypeLabel() != null && !violation.getTypeLabel().isBlank()) {
            return violation.getTypeLabel();
        }
        String typeCode = violation.getType();
        if (typeCode == null || typeCode.isBlank()) {
            return typeCode;
        }
        return systemSettingsService.getViolationTypes().stream()
                .filter(type -> typeCode.equals(type.getCode()))
                .map(ViolationTypeOptionResponse::getLabel)
                .findFirst()
                .orElse(typeCode);
    }

    private RaceResultResponse mapResult(RaceResult result) {
        return RaceResultResponse.builder()
                .id(result.getId())
                .raceId(result.getRace().getId())
                .participantId(result.getParticipant().getId())
                .ownerId(result.getOwner().getId())
                .ownerUsername(result.getOwner().getUsername())
                .horseId(result.getHorse().getId())
                .horseName(result.getHorse().getName())
                .jockeyId(result.getJockey().getId())
                .jockeyUsername(result.getJockey().getUsername())
                .rank(result.getRank())
                .finishTimeMillis(result.getFinishTimeMillis())
                .status(result.getStatus())
                .jockeyChallengePoints(result.getJockeyChallengePoints())
                .prizeAmount(defaultZero(result.getPrizeAmount()))
                .ownerPrizeAmount(ownerRacePrizeAmount(result))
                .jockeyPrizeAmount(defaultZero(result.getJockeyPrizeAmount()))
                .jockeyPrizePercent(defaultZero(result.getJockeyPrizePercent()))
                .payoutStatus(result.getPayoutStatus())
                .note(result.getNote())
                .finalizedBy(result.getFinalizedBy())
                .finalizedAt(result.getFinalizedAt())
                .build();
    }

    private JockeyChallengeStandingResponse mapChallengeResult(JockeyChallengeResult result) {
        return JockeyChallengeStandingResponse.builder()
                .jockeyId(result.getJockey().getId())
                .jockeyUsername(result.getJockey().getUsername())
                .totalPoints(result.getTotalPoints())
                .firstPlaces(result.getFirstPlaces())
                .secondPlaces(result.getSecondPlaces())
                .thirdPlaces(result.getThirdPlaces())
                .challengeRank(result.getChallengeRank())
                .prizeAmount(result.getPrizeAmount())
                .payoutStatus(result.getPayoutStatus())
                .finalizedAt(result.getFinalizedAt())
                .build();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal ownerRacePrizeAmount(RaceResult result) {
        BigDecimal ownerAmount = defaultZero(result.getOwnerPrizeAmount());
        BigDecimal jockeyAmount = defaultZero(result.getJockeyPrizeAmount());
        if (ownerAmount.compareTo(BigDecimal.ZERO) == 0 && jockeyAmount.compareTo(BigDecimal.ZERO) == 0) {
            return defaultZero(result.getPrizeAmount());
        }
        return ownerAmount;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record JockeyStanding(User jockey, int totalPoints, int firstPlaces, int secondPlaces, int thirdPlaces,
                                  int rank, BigDecimal prizeAmount) {
    }

    private static final class JockeyStandingAccumulator {
        private final User jockey;
        private int totalPoints;
        private int firstPlaces;
        private int secondPlaces;
        private int thirdPlaces;

        private JockeyStandingAccumulator(User jockey) {
            this.jockey = jockey;
        }

        private int totalPoints() {
            return totalPoints;
        }

        private int firstPlaces() {
            return firstPlaces;
        }

        private int secondPlaces() {
            return secondPlaces;
        }

        private int thirdPlaces() {
            return thirdPlaces;
        }

        private boolean sameScore(JockeyStandingAccumulator other) {
            return totalPoints == other.totalPoints
                    && firstPlaces == other.firstPlaces
                    && secondPlaces == other.secondPlaces
                    && thirdPlaces == other.thirdPlaces;
        }
    }
}
