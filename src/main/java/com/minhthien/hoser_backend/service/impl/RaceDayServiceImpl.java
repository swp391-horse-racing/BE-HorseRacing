package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceFinalizeResultRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationReviewRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationWithdrawRequest;
import com.minhthien.hoser_backend.dto.request.RaceResultEntryRequest;
import com.minhthien.hoser_backend.dto.response.JockeyChallengeStandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceRegistrationResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResultResponse;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.RaceDayService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RaceDayServiceImpl implements RaceDayService {
    private static final String RACE_REGISTRATION_REF = "RACE_REGISTRATION";
    private static final String RACE_RESULT_REF = "RACE_RESULT";
    private static final String JOCKEY_CHALLENGE_REF = "JOCKEY_CHALLENGE";

    private final RaceRepository raceRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceResultRepository raceResultRepository;
    private final JockeyChallengeResultRepository jockeyChallengeResultRepository;
    private final JockeyInvitationRepository jockeyInvitationRepository;
    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final TournamentServiceImpl tournamentService;
    private final FinanceSettingsService financeSettingsService;

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
        JockeyInvitation invitation = jockeyInvitationRepository.findById(request.getJockeyInvitationId())
                .orElseThrow(() -> new ResourceNotFoundException("JockeyInvitation", "id",
                        request.getJockeyInvitationId()));
        validateEligibleInvitation(ownerId, request.getHorseId(), invitation);
        List<RaceRegistrationStatus> activeStatuses = activeRegistrationStatuses();
        if (raceRegistrationRepository.existsByRaceIdAndHorseIdAndStatusIn(
                raceId, invitation.getHorse().getId(), activeStatuses)) {
            throw new BadRequestException("Horse is already registered for this race");
        }
        if (raceRegistrationRepository.existsActiveHorseRegistrationOnDay(invitation.getHorse().getId(),
                activeStatuses, tournament.getStartAt(), tournament.getEndAt())) {
            throw new BadRequestException("Horse can only join one race per tournament day");
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
        int gateNumber = request != null && request.getGateNumber() != null
                ? request.getGateNumber()
                : nextGateNumber(race.getId());
        if (gateNumber <= 0) {
            throw new BadRequestException("Gate number must be greater than zero");
        }
        if (raceParticipantRepository.existsByRaceIdAndGateNumber(race.getId(), gateNumber)) {
            throw new BadRequestException("Gate number already exists in this race");
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
                .gateNumber(gateNumber)
                .status(RaceParticipantStatus.REGISTERED)
                .build();
        raceParticipantRepository.save(participant);
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
        return mapRegistration(raceRegistrationRepository.save(registration));
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
        return mapRegistration(raceRegistrationRepository.save(registration));
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
    @Transactional
    public List<RaceResultResponse> finalizeRaceResult(Long refereeId, Long raceId, RaceFinalizeResultRequest request) {
        User referee = requireUser(refereeId);
        requireRole(referee, UserRole.REFEREE, "Only referees can finalize race results");
        Race race = requireRace(raceId);
        if (race.getReferee() == null || !race.getReferee().getId().equals(refereeId)) {
            throw new UnauthorizedException("Referee is not assigned to this race");
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
        validateResultEntries(request.getResults(), participantById);

        LocalDateTime now = LocalDateTime.now();
        List<RaceResult> results = request.getResults().stream()
                .map(entry -> buildRaceResult(race, participantById.get(entry.getParticipantId()), entry, refereeId, now))
                .toList();
        raceResultRepository.saveAll(results);
        results.forEach(this::payoutRacePrize);
        race.setStatus(RaceStatus.RESULT_CONFIRMED);
        race.setResultFinalizedAt(now);
        race.setResultFinalizedBy(refereeId);
        raceRepository.save(race);
        return raceResultRepository.findByRaceIdOrderByRankAsc(raceId).stream()
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
                List.of(RaceStatus.DRAFT, RaceStatus.SCHEDULED, RaceStatus.ONGOING));
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

    private void validateEligibleInvitation(Long ownerId, Long horseId, JockeyInvitation invitation) {
        if (horseId == null) {
            throw new BadRequestException("Horse id is required");
        }
        if (!invitation.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Owner does not own this jockey invitation");
        }
        if (!invitation.getHorse().getId().equals(horseId)) {
            throw new BadRequestException("Jockey invitation does not belong to the selected horse");
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
            if (!participantById.containsKey(entry.getParticipantId())) {
                throw new BadRequestException("Result participant does not belong to this race");
            }
            if (!participantIds.add(entry.getParticipantId())) {
                throw new BadRequestException("Duplicate participant in race result");
            }
            if (entry.getStatus() == RaceParticipantStatus.FINISHED) {
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

    private RaceResult buildRaceResult(Race race, RaceParticipant participant, RaceResultEntryRequest entry,
                                       Long refereeId, LocalDateTime now) {
        participant.setStatus(entry.getStatus());
        raceParticipantRepository.save(participant);
        BigDecimal prizeAmount = prizeAmountFor(race, entry.getRank());
        PrizeShare prizeShare = calculatePrizeShare(entry.getRank(), prizeAmount);
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
                .ownerPrizeAmount(prizeShare.ownerAmount())
                .jockeyPrizeAmount(prizeShare.jockeyAmount())
                .jockeyPrizePercent(prizeShare.jockeyPercent())
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
    }

    private void payoutChallengePrize(JockeyChallengeResult result) {
        if (result.getPayoutStatus() != RacePayoutStatus.PENDING
                || result.getPrizeAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!adminWalletCanPay(result.getPrizeAmount())) {
            result.setPayoutStatus(RacePayoutStatus.UNPAID);
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

    private PrizeShare calculatePrizeShare(Integer rank, BigDecimal prizeAmount) {
        BigDecimal total = defaultZero(prizeAmount).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(BigDecimal.ZERO) <= 0 || rank == null) {
            return new PrizeShare(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal jockeyPercent = financeSettingsService.getRacePrizeJockeyPercent(rank)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal jockeyAmount = total.multiply(jockeyPercent)
                .divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        BigDecimal ownerAmount = total.subtract(jockeyAmount).setScale(2, RoundingMode.HALF_UP);
        return new PrizeShare(ownerAmount, jockeyAmount, jockeyPercent);
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

    private int nextGateNumber(Long raceId) {
        return raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId).stream()
                .map(RaceParticipant::getGateNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;
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

    private record PrizeShare(BigDecimal ownerAmount, BigDecimal jockeyAmount, BigDecimal jockeyPercent) {
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
