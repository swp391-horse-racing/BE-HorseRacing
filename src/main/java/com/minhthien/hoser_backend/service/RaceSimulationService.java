package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RaceResultEntryRequest;
import com.minhthien.hoser_backend.dto.response.*;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.ConflictException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.simulation.RaceSimulationEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RaceSimulationService {
    public static final long PLAYBACK_DURATION_MS = 28_000L;

    private final RaceRepository raceRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceViolationRepository raceViolationRepository;
    private final RaceSimulationRepository raceSimulationRepository;
    private final RaceResultDraftRepository raceResultDraftRepository;
    private final RaceSimulationEngine engine;
    private final RealtimeEventService realtimeEventService;

    @Transactional(readOnly = true)
    public RaceSimulationResponse get(Long refereeId, Long raceId) {
        requireAssignedRace(refereeId, raceId);
        return raceSimulationRepository.findByRaceId(raceId)
                .map(this::mapSimulation)
                .orElse(null);
    }

    @Transactional
    public RaceSimulationResponse generate(Long refereeId, Long raceId) {
        Race race = requireAssignedRaceForUpdate(refereeId, raceId);
        Optional<RaceSimulation> existing = raceSimulationRepository.findByRaceIdForUpdate(raceId);
        if (existing.isPresent()) {
            return mapSimulation(existing.get());
        }
        if (race.getStatus() != RaceStatus.ONGOING) {
            throw new ConflictException("Only ongoing races can be simulated");
        }
        if (raceResultRepository.existsByRaceId(raceId)) {
            throw new ConflictException("Race already has official results");
        }

        List<RaceParticipant> participants = raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(raceId)
                .stream()
                .filter(item -> item.getStatus() == RaceParticipantStatus.CHECKED_IN)
                .toList();
        if (participants.size() < Math.max(2, race.getMinParticipants())) {
            throw new ConflictException("Race does not have enough checked-in participants for simulation");
        }
        validateGates(participants);

        Map<Long, PerformanceStats> horseStats = performanceMap(
                raceResultRepository.findFinalizedHorseSimulationStatistics(raceId));
        Map<Long, PerformanceStats> jockeyStats = performanceMap(
                raceResultRepository.findFinalizedJockeySimulationStatistics(raceId));
        List<RaceSimulationEngine.SimulationInput> inputs = participants.stream()
                .map(participant -> simulationInput(participant, horseStats, jockeyStats))
                .toList();

        String seed = UUID.randomUUID() + "-" + UUID.randomUUID();
        String runId = UUID.randomUUID().toString();
        LocalDateTime generatedAt = LocalDateTime.now();
        RaceSimulation simulation = RaceSimulation.builder()
                .race(race)
                .runId(runId)
                .seed(seed)
                .status(RaceSimulationStatus.GENERATED)
                .playbackDurationMs(PLAYBACK_DURATION_MS)
                .generatedAt(generatedAt)
                .playbackEndsAt(generatedAt.plusNanos(PLAYBACK_DURATION_MS * 1_000_000L))
                .generatedBy(refereeId)
                .build();

        Map<Long, RaceParticipant> participantById = participants.stream()
                .collect(Collectors.toMap(RaceParticipant::getId, item -> item));
        engine.simulate(inputs, seed, race.getDistance()).forEach(result -> {
            RaceSimulationEngine.SimulationInput input = result.input();
            RaceSimulationParticipant item = RaceSimulationParticipant.builder()
                    .participant(participantById.get(input.participantId()))
                    .horseId(input.horseId())
                    .horseName(input.horseName())
                    .jockeyId(input.jockeyId())
                    .jockeyName(input.jockeyName())
                    .gateNumber(input.gateNumber())
                    .horseStarts(input.horseStarts())
                    .horseWins(input.horseWins())
                    .horseWinRate(input.horseWinRate())
                    .jockeyStarts(input.jockeyStarts())
                    .jockeyWins(input.jockeyWins())
                    .jockeyWinRate(input.jockeyWinRate())
                    .historyScore(result.historyScore())
                    .rank(result.rank())
                    .finishTimeMillis(result.finishTimeMillis())
                    .build();
            result.checkpoints().forEach(checkpoint -> item.addCheckpoint(
                    RaceSimulationCheckpoint.builder()
                            .tick(checkpoint.tick())
                            .at(checkpoint.at())
                            .progress(checkpoint.progress())
                            .build()));
            simulation.addParticipant(item);
        });
        RaceSimulation saved = raceSimulationRepository.save(simulation);
        realtimeEventService.publishRaceStatus(race, "SIMULATION_GENERATED", race.getStatus().name(), runId);
        return mapSimulation(saved);
    }

    @Transactional
    public RaceSimulationConfirmResponse confirm(Long refereeId, Long raceId, String runId) {
        requireAssignedRaceForUpdate(refereeId, raceId);
        RaceSimulation simulation = raceSimulationRepository.findByRaceIdForUpdate(raceId)
                .orElseThrow(() -> new ConflictException("Race has no simulation to confirm"));
        if (!simulation.getRunId().equals(runId)) {
            throw new ConflictException("Simulation run id is invalid");
        }
        Optional<RaceResultDraft> existingDraft = raceResultDraftRepository.findByRaceIdForUpdate(raceId);
        if (existingDraft.isPresent()) {
            return RaceSimulationConfirmResponse.builder()
                    .simulation(mapSimulation(simulation))
                    .resultDraft(mapDraft(existingDraft.get()))
                    .build();
        }
        if (simulation.getStatus() != RaceSimulationStatus.GENERATED) {
            throw new ConflictException("Simulation cannot be confirmed in its current state");
        }
        if (LocalDateTime.now().isBefore(simulation.getPlaybackEndsAt())) {
            throw new ConflictException("Wait for the simulation playback to finish before confirming");
        }
        if (simulation.getRace().getStatus() != RaceStatus.ONGOING) {
            throw new ConflictException("Race is no longer ongoing");
        }

        LocalDateTime now = LocalDateTime.now();
        RaceResultDraft draft = RaceResultDraft.builder()
                .race(simulation.getRace())
                .simulation(simulation)
                .status(RaceResultDraftStatus.REVIEW_PENDING)
                .draftVersion(1L)
                .createdAt(now)
                .createdBy(refereeId)
                .updatedAt(now)
                .updatedBy(refereeId)
                .build();
        simulation.getParticipants().forEach(item -> draft.addRow(
                RaceResultDraftRow.builder()
                        .participant(item.getParticipant())
                        .baseRank(item.getRank())
                        .rank(item.getRank())
                        .baseFinishTimeMillis(item.getFinishTimeMillis())
                        .penaltyTimeMillis(0L)
                        .finishTimeMillis(item.getFinishTimeMillis())
                        .status(RaceParticipantStatus.FINISHED)
                        .build()));
        applyViolations(draft, raceViolationRepository.findByRaceIdOrderByOccurredAtDesc(raceId), false, refereeId);
        raceResultDraftRepository.save(draft);
        simulation.setStatus(RaceSimulationStatus.DRAFTED);
        simulation.setConfirmedAt(now);
        simulation.setConfirmedBy(refereeId);
        raceSimulationRepository.save(simulation);
        realtimeEventService.publishRaceStatus(simulation.getRace(), "SIMULATION_DRAFTED",
                simulation.getRace().getStatus().name(), runId);
        return RaceSimulationConfirmResponse.builder()
                .simulation(mapSimulation(simulation))
                .resultDraft(mapDraft(draft))
                .build();
    }

    @Transactional(readOnly = true)
    public RaceResultDraftResponse getDraft(Long refereeId, Long raceId) {
        requireAssignedRace(refereeId, raceId);
        return raceResultDraftRepository.findByRaceId(raceId)
                .map(this::mapDraft)
                .orElse(null);
    }

    @Transactional
    public RaceResultDraftResponse recalculateDraft(Long refereeId, Long raceId) {
        RaceResultDraft draft = raceResultDraftRepository.findByRaceIdForUpdate(raceId).orElse(null);
        if (draft == null) {
            return null;
        }
        requireAssignedRace(refereeId, raceId);
        if (draft.getStatus() != RaceResultDraftStatus.REVIEW_PENDING) {
            throw new ConflictException("Published result draft cannot be changed");
        }
        applyViolations(draft, raceViolationRepository.findByRaceIdOrderByOccurredAtDesc(raceId), true, refereeId);
        return mapDraft(raceResultDraftRepository.save(draft));
    }

    @Transactional
    public DraftFinalizeContext prepareDraftFinalize(Long refereeId, Long raceId, Long requestedVersion) {
        requireAssignedRace(refereeId, raceId);
        RaceResultDraft draft = raceResultDraftRepository.findByRaceIdForUpdate(raceId)
                .orElseThrow(() -> new ConflictException("Race result draft does not exist"));
        if (draft.getStatus() != RaceResultDraftStatus.REVIEW_PENDING) {
            throw new ConflictException("Race result draft has already been published");
        }
        if (requestedVersion == null || !requestedVersion.equals(draft.getDraftVersion())) {
            throw new ConflictException("Race result draft version is stale");
        }
        List<RaceResultEntryRequest> entries = draft.getRows().stream()
                .map(this::toFinalizeEntry)
                .toList();
        return new DraftFinalizeContext(entries, draft.getSimulation().getRunId(), draft);
    }

    @Transactional
    public void markPublished(RaceResultDraft draft, Long refereeId) {
        LocalDateTime now = LocalDateTime.now();
        draft.setStatus(RaceResultDraftStatus.PUBLISHED);
        draft.setPublishedAt(now);
        draft.setPublishedBy(refereeId);
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(refereeId);
        raceResultDraftRepository.save(draft);
        RaceSimulation simulation = draft.getSimulation();
        simulation.setStatus(RaceSimulationStatus.CONFIRMED);
        raceSimulationRepository.save(simulation);
    }

    @Transactional(readOnly = true)
    public boolean hasSimulation(Long raceId) {
        return raceSimulationRepository.existsByRaceId(raceId);
    }

    private void applyViolations(RaceResultDraft draft, List<RaceViolation> violations,
                                 boolean incrementVersion, Long refereeId) {
        Map<Long, List<RaceViolation>> byParticipant = violations.stream()
                .collect(Collectors.groupingBy(item -> item.getParticipant().getId()));
        for (RaceResultDraftRow row : draft.getRows()) {
            List<RaceViolation> participantViolations =
                    byParticipant.getOrDefault(row.getParticipant().getId(), List.of());
            boolean disqualified = participantViolations.stream()
                    .anyMatch(item -> item.getResultAction() == ViolationResultAction.DISQUALIFY);
            long penalty = participantViolations.stream()
                    .filter(item -> item.getResultAction() == ViolationResultAction.TIME_PENALTY)
                    .mapToLong(item -> Math.max(0L, defaultLong(item.getTimePenaltyMillis())))
                    .sum();
            row.setPenaltyTimeMillis(penalty);
            if (disqualified) {
                row.setStatus(RaceParticipantStatus.DISQUALIFIED);
                row.setRank(null);
                row.setFinishTimeMillis(null);
                row.setDisqualificationReason(participantViolations.stream()
                        .filter(item -> item.getResultAction() == ViolationResultAction.DISQUALIFY)
                        .map(item -> item.getTypeLabel() + ": " + item.getDescription())
                        .collect(Collectors.joining("; ")));
            } else {
                row.setStatus(RaceParticipantStatus.FINISHED);
                row.setFinishTimeMillis(row.getBaseFinishTimeMillis() + penalty);
                row.setDisqualificationReason(null);
            }
        }
        List<RaceResultDraftRow> finished = draft.getRows().stream()
                .filter(item -> item.getStatus() == RaceParticipantStatus.FINISHED)
                .sorted(Comparator.comparing(RaceResultDraftRow::getFinishTimeMillis)
                        .thenComparing(RaceResultDraftRow::getBaseRank)
                        .thenComparing(item -> item.getParticipant().getId()))
                .toList();
        for (int index = 0; index < finished.size(); index++) {
            finished.get(index).setRank(index + 1);
        }
        if (incrementVersion) {
            draft.setDraftVersion(draft.getDraftVersion() + 1L);
        }
        draft.setUpdatedAt(LocalDateTime.now());
        draft.setUpdatedBy(refereeId);
    }

    private RaceResultEntryRequest toFinalizeEntry(RaceResultDraftRow row) {
        RaceResultEntryRequest entry = new RaceResultEntryRequest();
        entry.setParticipantId(row.getParticipant().getId());
        entry.setRank(row.getRank());
        entry.setFinishTimeMillis(row.getFinishTimeMillis());
        entry.setStatus(row.getStatus());
        entry.setNote(row.getStatus() == RaceParticipantStatus.DISQUALIFIED
                ? row.getDisqualificationReason()
                : row.getPenaltyTimeMillis() > 0
                ? "Simulation time penalty +" + row.getPenaltyTimeMillis() + "ms"
                : null);
        return entry;
    }

    private RaceSimulationEngine.SimulationInput simulationInput(
            RaceParticipant participant,
            Map<Long, PerformanceStats> horseStats,
            Map<Long, PerformanceStats> jockeyStats) {
        PerformanceStats horse = horseStats.getOrDefault(participant.getHorse().getId(), PerformanceStats.empty());
        PerformanceStats jockey = jockeyStats.getOrDefault(participant.getJockey().getId(), PerformanceStats.empty());
        return new RaceSimulationEngine.SimulationInput(
                participant.getId(),
                participant.getHorse().getId(),
                participant.getHorse().getName(),
                participant.getJockey().getId(),
                participant.getJockey().getUsername(),
                participant.getGateNumber(),
                horse.starts(), horse.wins(), horse.winRate(),
                jockey.starts(), jockey.wins(), jockey.winRate());
    }

    private Map<Long, PerformanceStats> performanceMap(List<Object[]> rows) {
        Map<Long, PerformanceStats> result = new HashMap<>();
        for (Object[] row : rows) {
            long starts = ((Number) row[1]).longValue();
            long wins = ((Number) row[2]).longValue();
            result.put(((Number) row[0]).longValue(),
                    new PerformanceStats(starts, wins, (wins + 1.0) / (starts + 2.0)));
        }
        return result;
    }

    private void validateGates(List<RaceParticipant> participants) {
        Set<Integer> gates = new HashSet<>();
        for (RaceParticipant participant : participants) {
            Integer gate = participant.getGateNumber();
            if (gate == null || gate <= 0 || !gates.add(gate)) {
                throw new ConflictException("Checked-in participants need unique positive gate numbers");
            }
        }
    }

    private Race requireAssignedRace(Long refereeId, Long raceId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
        assertAssigned(refereeId, race);
        return race;
    }

    private Race requireAssignedRaceForUpdate(Long refereeId, Long raceId) {
        Race race = raceRepository.findByIdForUpdate(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
        assertAssigned(refereeId, race);
        return race;
    }

    private void assertAssigned(Long refereeId, Race race) {
        if (race.getReferee() == null || !race.getReferee().getId().equals(refereeId)) {
            throw new UnauthorizedException("Referee is not assigned to this race");
        }
    }

    private RaceSimulationResponse mapSimulation(RaceSimulation simulation) {
        return RaceSimulationResponse.builder()
                .raceId(simulation.getRace().getId())
                .runId(simulation.getRunId())
                .status(simulation.getStatus())
                .algorithmVersion(simulation.getAlgorithmVersion())
                .seed(simulation.getSeed())
                .playbackDurationMs(simulation.getPlaybackDurationMs())
                .generatedAt(simulation.getGeneratedAt())
                .playbackEndsAt(simulation.getPlaybackEndsAt())
                .confirmedAt(simulation.getConfirmedAt())
                .serverTime(LocalDateTime.now())
                .participants(simulation.getParticipants().stream()
                        .map(this::mapSimulationParticipant)
                        .toList())
                .build();
    }

    private RaceSimulationParticipantResponse mapSimulationParticipant(RaceSimulationParticipant item) {
        return RaceSimulationParticipantResponse.builder()
                .participantId(item.getParticipant().getId())
                .horseId(item.getHorseId())
                .horseName(item.getHorseName())
                .jockeyId(item.getJockeyId())
                .jockeyName(item.getJockeyName())
                .gateNumber(item.getGateNumber())
                .horseStarts(item.getHorseStarts())
                .horseWins(item.getHorseWins())
                .horseWinRate(item.getHorseWinRate())
                .jockeyStarts(item.getJockeyStarts())
                .jockeyWins(item.getJockeyWins())
                .jockeyWinRate(item.getJockeyWinRate())
                .historyScore(item.getHistoryScore())
                .rank(item.getRank())
                .finishTimeMillis(item.getFinishTimeMillis())
                .checkpoints(item.getCheckpoints().stream()
                        .map(checkpoint -> RaceSimulationCheckpointResponse.builder()
                                .tick(checkpoint.getTick())
                                .at(checkpoint.getAt())
                                .progress(checkpoint.getProgress())
                                .build())
                        .toList())
                .build();
    }

    private RaceResultDraftResponse mapDraft(RaceResultDraft draft) {
        return RaceResultDraftResponse.builder()
                .status(draft.getStatus())
                .source(RaceResultSource.SIMULATION)
                .simulationRunId(draft.getSimulation().getRunId())
                .version(draft.getDraftVersion())
                .createdAt(draft.getCreatedAt())
                .updatedAt(draft.getUpdatedAt())
                .publishedAt(draft.getPublishedAt())
                .rows(draft.getRows().stream()
                        .sorted(Comparator
                                .comparing((RaceResultDraftRow row) -> row.getRank() == null ? Integer.MAX_VALUE : row.getRank())
                                .thenComparing(RaceResultDraftRow::getBaseRank))
                        .map(this::mapDraftRow)
                        .toList())
                .build();
    }

    private RaceResultDraftRowResponse mapDraftRow(RaceResultDraftRow row) {
        RaceParticipant participant = row.getParticipant();
        return RaceResultDraftRowResponse.builder()
                .participantId(participant.getId())
                .horseId(participant.getHorse().getId())
                .horseName(participant.getHorse().getName())
                .jockeyId(participant.getJockey().getId())
                .jockeyName(participant.getJockey().getUsername())
                .gateNumber(participant.getGateNumber())
                .baseRank(row.getBaseRank())
                .rank(row.getRank())
                .baseFinishTimeMillis(row.getBaseFinishTimeMillis())
                .penaltyTimeMillis(row.getPenaltyTimeMillis())
                .finishTimeMillis(row.getFinishTimeMillis())
                .status(row.getStatus())
                .disqualificationReason(row.getDisqualificationReason())
                .build();
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private record PerformanceStats(long starts, long wins, double winRate) {
        private static PerformanceStats empty() {
            return new PerformanceStats(0L, 0L, 0.5);
        }
    }

    public record DraftFinalizeContext(
            List<RaceResultEntryRequest> entries,
            String simulationRunId,
            RaceResultDraft draft
    ) {
    }
}
