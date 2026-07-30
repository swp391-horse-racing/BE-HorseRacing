package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.JockeyChallengeStandingResponse;
import com.minhthien.hoser_backend.dto.response.TournamentFinalizationResponse;
import com.minhthien.hoser_backend.dto.response.TournamentLeaderboardEntryResponse;
import com.minhthien.hoser_backend.dto.response.TournamentLeaderboardResponse;
import com.minhthien.hoser_backend.dto.response.TournamentPayoutResponse;
import com.minhthien.hoser_backend.dto.response.TournamentStatisticsResponse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.TournamentLeaderboardSnapshot;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import com.minhthien.hoser_backend.enums.RacePayoutStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentLeaderboardSnapshotRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.RaceDayService;
import com.minhthien.hoser_backend.service.TournamentFinalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentFinalizationServiceImpl implements TournamentFinalizationService {
    private static final Set<RacePayoutStatus> FINAL_PAYOUT_STATUSES = EnumSet.of(
            RacePayoutStatus.PAID,
            RacePayoutStatus.UNPAID,
            RacePayoutStatus.NOT_ELIGIBLE
    );

    private final TournamentRepository tournamentRepository;
    private final RaceRepository raceRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceComplaintRepository raceComplaintRepository;
    private final TournamentLeaderboardSnapshotRepository leaderboardSnapshotRepository;
    private final UserRepository userRepository;
    private final RaceDayService raceDayService;
    private final TournamentServiceImpl tournamentService;

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentFinalizationResponse finalizeTournament(Long adminId, Long tournamentId) {
        User admin = requireAdmin(adminId);
        Tournament tournament = requireTournament(tournamentId);

        if (tournament.getStatus() == TournamentStatus.COMPLETED
                && leaderboardSnapshotRepository.existsByTournamentId(tournamentId)) {
            return buildFinalizationResponse(tournament);
        }

        validateCanFinalize(tournamentId, tournament);

        if (Boolean.TRUE.equals(tournament.getJockeyChallengeEnabled())) {
            raceDayService.finalizeJockeyChallenge(adminId, tournamentId);
        }

        LocalDateTime now = LocalDateTime.now();
        long pendingComplaints = raceComplaintRepository.countByRaceTournamentIdAndStatus(
                tournamentId, RaceComplaintStatus.PENDING);
        tournament.setStatus(TournamentStatus.COMPLETED);
        tournament.setFinalizedAt(now);
        tournament.setFinalizedBy(adminId);
        tournament.setPendingComplaintCountAtFinalize(Math.toIntExact(pendingComplaints));
        tournament.setUpdatedBy(admin.getUsername());
        Tournament saved = tournamentRepository.save(tournament);

        if (!leaderboardSnapshotRepository.existsByTournamentId(tournamentId)) {
            List<TournamentLeaderboardSnapshot> snapshots = raceResultRepository.findByRaceTournamentId(tournamentId)
                    .stream()
                    .sorted(resultComparator())
                    .map(result -> buildSnapshot(saved, result, adminId, now))
                    .toList();
            leaderboardSnapshotRepository.saveAll(snapshots);
        }

        return buildFinalizationResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TournamentLeaderboardResponse getLeaderboard(Long tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (!isPublicStatus(tournament.getStatus())) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        return buildLeaderboard(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public TournamentStatisticsResponse getStatistics(Long adminId, Long tournamentId) {
        requireAdmin(adminId);
        Tournament tournament = requireTournament(tournamentId);
        return buildStatistics(tournament);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentPayoutResponse> getPayouts(Long adminId, Long tournamentId) {
        requireAdmin(adminId);
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        return raceResultRepository.findByRaceTournamentId(tournamentId).stream()
                .sorted(resultComparator())
                .map(this::mapPayout)
                .toList();
    }

    private void validateCanFinalize(Long tournamentId, Tournament tournament) {
        if (tournament.getStatus() == TournamentStatus.CANCELLED) {
            throw new BadRequestException("Cancelled tournaments cannot be finalized");
        }

        List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournamentId);
        if (races.isEmpty()) {
            throw new BadRequestException("Tournament must have races before finalizing");
        }

        List<Race> unfinished = races.stream()
                .filter(race -> race.getStatus() != RaceStatus.RESULT_CONFIRMED
                        && race.getStatus() != RaceStatus.CANCELLED)
                .toList();
        if (!unfinished.isEmpty()) {
            throw new BadRequestException("All races must be result-confirmed or cancelled before finalizing");
        }

        List<Race> confirmedRaces = races.stream()
                .filter(race -> race.getStatus() == RaceStatus.RESULT_CONFIRMED)
                .toList();
        List<RaceResult> results = raceResultRepository.findByRaceTournamentId(tournamentId);
        if (confirmedRaces.isEmpty() || results.isEmpty()) {
            throw new BadRequestException("Tournament must have at least one confirmed race result");
        }

        Map<Long, Long> resultCountByRace = results.stream()
                .collect(Collectors.groupingBy(result -> result.getRace().getId(), Collectors.counting()));
        boolean missingResults = confirmedRaces.stream()
                .anyMatch(race -> resultCountByRace.getOrDefault(race.getId(), 0L) == 0L);
        if (missingResults) {
            throw new BadRequestException("Every confirmed race must have result rows before finalizing");
        }

        boolean hasInvalidPayout = results.stream()
                .map(RaceResult::getPayoutStatus)
                .anyMatch(status -> status == null || !FINAL_PAYOUT_STATUSES.contains(status));
        if (hasInvalidPayout) {
            throw new BadRequestException("Race prize payouts must be paid, unpaid, or not eligible before finalizing");
        }
    }

    private TournamentLeaderboardSnapshot buildSnapshot(Tournament tournament, RaceResult result,
                                                        Long adminId, LocalDateTime now) {
        Race race = result.getRace();
        return TournamentLeaderboardSnapshot.builder()
                .tournament(tournament)
                .raceId(race.getId())
                .raceName(race.getName())
                .raceScheduledStartAt(race.getScheduledStartAt())
                .raceScheduledEndAt(race.getScheduledEndAt())
                .raceResultId(result.getId())
                .participantId(result.getParticipant().getId())
                .raceRank(result.getRank())
                .finishTimeMillis(result.getFinishTimeMillis())
                .resultStatus(result.getStatus())
                .horseId(result.getHorse().getId())
                .horseName(result.getHorse().getName())
                .ownerId(result.getOwner().getId())
                .ownerUsername(result.getOwner().getUsername())
                .jockeyId(result.getJockey().getId())
                .jockeyUsername(result.getJockey().getUsername())
                .prizeAmount(defaultZero(result.getPrizeAmount()))
                .ownerPrizeAmount(ownerRacePrizeAmount(result))
                .jockeyPrizeAmount(defaultZero(result.getJockeyPrizeAmount()))
                .jockeyPrizePercent(defaultZero(result.getJockeyPrizePercent()))
                .payoutStatus(result.getPayoutStatus())
                .resultFinalizedBy(result.getFinalizedBy())
                .resultFinalizedAt(result.getFinalizedAt())
                .tournamentFinalizedBy(adminId)
                .tournamentFinalizedAt(now)
                .build();
    }

    private TournamentFinalizationResponse buildFinalizationResponse(Tournament tournament) {
        return TournamentFinalizationResponse.builder()
                .tournament(tournamentService.mapToResponse(tournament))
                .leaderboard(buildLeaderboard(tournament))
                .statistics(buildStatistics(tournament))
                .payouts(raceResultRepository.findByRaceTournamentId(tournament.getId()).stream()
                        .sorted(resultComparator())
                        .map(this::mapPayout)
                        .toList())
                .build();
    }

    private TournamentLeaderboardResponse buildLeaderboard(Tournament tournament) {
        List<TournamentLeaderboardEntryResponse> entries = leaderboardSnapshotRepository
                .findByTournamentIdOrderByRaceScheduledStartAtAscRaceRankAscIdAsc(tournament.getId())
                .stream()
                .map(this::mapLeaderboardEntry)
                .toList();
        List<JockeyChallengeStandingResponse> jockeyStandings = Boolean.TRUE.equals(tournament.getJockeyChallengeEnabled())
                ? raceDayService.getJockeyChallengeStandings(tournament.getId())
                : List.of();
        return TournamentLeaderboardResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .tournamentStatus(tournament.getStatus())
                .finalizedAt(tournament.getFinalizedAt())
                .finalizedBy(tournament.getFinalizedBy())
                .pendingComplaintCountAtFinalize(tournament.getPendingComplaintCountAtFinalize())
                .entries(entries)
                .jockeyStandings(jockeyStandings)
                .build();
    }

    private TournamentStatisticsResponse buildStatistics(Tournament tournament) {
        Long tournamentId = tournament.getId();
        List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournamentId);
        List<RaceRegistration> registrations = raceRegistrationRepository.findByRaceTournamentIdOrderByCreatedAtDesc(tournamentId);
        List<RaceParticipant> participants = raceParticipantRepository.findByRaceTournamentId(tournamentId);
        List<RaceComplaint> complaints = raceComplaintRepository.findByRaceTournamentId(tournamentId);
        List<RaceResult> results = raceResultRepository.findByRaceTournamentId(tournamentId);

        Map<RacePayoutStatus, BigDecimal> payoutTotals = results.stream()
                .collect(Collectors.groupingBy(RaceResult::getPayoutStatus,
                        Collectors.mapping(result -> defaultZero(result.getPrizeAmount()),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        BigDecimal paidPrizeAmount = payoutTotals.getOrDefault(RacePayoutStatus.PAID, BigDecimal.ZERO);
        BigDecimal unpaidPrizeAmount = payoutTotals.getOrDefault(RacePayoutStatus.UNPAID, BigDecimal.ZERO);
        BigDecimal notEligiblePrizeAmount = payoutTotals.getOrDefault(RacePayoutStatus.NOT_ELIGIBLE, BigDecimal.ZERO);

        return TournamentStatisticsResponse.builder()
                .tournamentId(tournamentId)
                .tournamentName(tournament.getName())
                .tournamentStatus(tournament.getStatus())
                .finalizedAt(tournament.getFinalizedAt())
                .finalizedBy(tournament.getFinalizedBy())
                .ownerCount(countDistinct(registrations, registration -> registration.getOwner().getId()))
                .horseCount(countDistinct(registrations, registration -> registration.getHorse().getId()))
                .jockeyCount(countDistinct(registrations, registration -> registration.getJockey().getId()))
                .refereeCount(countDistinct(races, race -> race.getReferee() == null ? null : race.getReferee().getId()))
                .raceResultCount(results.size())
                .pendingComplaintCountAtFinalize(tournament.getPendingComplaintCountAtFinalize())
                .registrationsByStatus(countByName(registrations, registration -> registration.getStatus().name()))
                .racesByStatus(countByName(races, race -> race.getStatus().name()))
                .participantsByStatus(countByName(participants, participant -> participant.getStatus().name()))
                .complaintsByStatus(countByName(complaints, complaint -> complaint.getStatus().name()))
                .prizePayoutTotalsByStatus(sumPayoutTotalsByName(payoutTotals))
                .totalPrizeAmount(results.stream()
                        .map(result -> defaultZero(result.getPrizeAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .paidPrizeAmount(paidPrizeAmount)
                .unpaidPrizeAmount(unpaidPrizeAmount)
                .notEligiblePrizeAmount(notEligiblePrizeAmount)
                .build();
    }

    private TournamentLeaderboardEntryResponse mapLeaderboardEntry(TournamentLeaderboardSnapshot snapshot) {
        return TournamentLeaderboardEntryResponse.builder()
                .id(snapshot.getId())
                .tournamentId(snapshot.getTournament().getId())
                .raceId(snapshot.getRaceId())
                .raceName(snapshot.getRaceName())
                .raceScheduledStartAt(snapshot.getRaceScheduledStartAt())
                .raceScheduledEndAt(snapshot.getRaceScheduledEndAt())
                .raceResultId(snapshot.getRaceResultId())
                .participantId(snapshot.getParticipantId())
                .raceRank(snapshot.getRaceRank())
                .finishTimeMillis(snapshot.getFinishTimeMillis())
                .resultStatus(snapshot.getResultStatus())
                .horseId(snapshot.getHorseId())
                .horseName(snapshot.getHorseName())
                .ownerId(snapshot.getOwnerId())
                .ownerUsername(snapshot.getOwnerUsername())
                .jockeyId(snapshot.getJockeyId())
                .jockeyUsername(snapshot.getJockeyUsername())
                .prizeAmount(defaultZero(snapshot.getPrizeAmount()))
                .ownerPrizeAmount(defaultZero(snapshot.getOwnerPrizeAmount()))
                .jockeyPrizeAmount(defaultZero(snapshot.getJockeyPrizeAmount()))
                .jockeyPrizePercent(defaultZero(snapshot.getJockeyPrizePercent()))
                .payoutStatus(snapshot.getPayoutStatus())
                .resultFinalizedBy(snapshot.getResultFinalizedBy())
                .resultFinalizedAt(snapshot.getResultFinalizedAt())
                .tournamentFinalizedBy(snapshot.getTournamentFinalizedBy())
                .tournamentFinalizedAt(snapshot.getTournamentFinalizedAt())
                .build();
    }

    private TournamentPayoutResponse mapPayout(RaceResult result) {
        BigDecimal ownerAmount = ownerRacePrizeAmount(result);
        BigDecimal jockeyAmount = defaultZero(result.getJockeyPrizeAmount());
        boolean unpaid = result.getPayoutStatus() == RacePayoutStatus.UNPAID;
        return TournamentPayoutResponse.builder()
                .raceResultId(result.getId())
                .tournamentId(result.getRace().getTournament().getId())
                .tournamentName(result.getRace().getTournament().getName())
                .raceId(result.getRace().getId())
                .raceName(result.getRace().getName())
                .participantId(result.getParticipant().getId())
                .rank(result.getRank())
                .horseId(result.getHorse().getId())
                .horseName(result.getHorse().getName())
                .ownerId(result.getOwner().getId())
                .ownerUsername(result.getOwner().getUsername())
                .jockeyId(result.getJockey().getId())
                .jockeyUsername(result.getJockey().getUsername())
                .prizeAmount(defaultZero(result.getPrizeAmount()))
                .ownerPrizeAmount(ownerAmount)
                .jockeyPrizeAmount(jockeyAmount)
                .jockeyPrizePercent(defaultZero(result.getJockeyPrizePercent()))
                .unpaidOwnerAmount(unpaid ? ownerAmount : BigDecimal.ZERO)
                .unpaidJockeyAmount(unpaid ? jockeyAmount : BigDecimal.ZERO)
                .payoutStatus(result.getPayoutStatus())
                .finalizedAt(result.getFinalizedAt())
                .build();
    }

    private Comparator<RaceResult> resultComparator() {
        return Comparator
                .comparing((RaceResult result) -> result.getRace().getScheduledStartAt(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(result -> result.getRank(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RaceResult::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private <T> Integer countDistinct(List<T> items, Function<T, Long> mapper) {
        return Math.toIntExact(items.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .distinct()
                .count());
    }

    private <T> Map<String, Long> countByName(List<T> items, Function<T, String> mapper) {
        return items.stream()
                .map(mapper)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, BigDecimal> sumPayoutTotalsByName(Map<RacePayoutStatus, BigDecimal> payoutTotals) {
        return payoutTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue,
                        BigDecimal::add, LinkedHashMap::new));
    }

    private BigDecimal ownerRacePrizeAmount(RaceResult result) {
        BigDecimal ownerAmount = defaultZero(result.getOwnerPrizeAmount());
        BigDecimal jockeyAmount = defaultZero(result.getJockeyPrizeAmount());
        if (ownerAmount.compareTo(BigDecimal.ZERO) == 0 && jockeyAmount.compareTo(BigDecimal.ZERO) == 0) {
            return defaultZero(result.getPrizeAmount());
        }
        return ownerAmount;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Tournament requireTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admins can finalize tournaments");
        }
        return admin;
    }

    private boolean isPublicStatus(TournamentStatus status) {
        return List.of(
                TournamentStatus.PUBLISHED,
                TournamentStatus.OPEN_REGISTRATION,
                TournamentStatus.REGISTRATION_CLOSED,
                TournamentStatus.SCHEDULED,
                TournamentStatus.ONGOING,
                TournamentStatus.COMPLETED
        ).contains(status);
    }
}
