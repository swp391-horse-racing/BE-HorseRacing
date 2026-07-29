package com.minhthien.hoser_backend.service.simulation;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RaceSimulationEngine {
    public static final int TOTAL_TICKS = 28;
    public static final int RANDOM_TICKS = 14;
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    public List<SimulationResult> simulate(List<SimulationInput> inputs, String seed, String distanceValue) {
        if (inputs == null || inputs.size() < 2) {
            throw new IllegalArgumentException("At least two participants are required");
        }
        SplittableRandom random = new SplittableRandom(seedToLong(seed));
        List<WorkingParticipant> working = inputs.stream()
                .sorted(Comparator.comparing(SimulationInput::participantId))
                .map(input -> buildParticipant(input, random))
                .toList();

        List<WorkingParticipant> ranked = new ArrayList<>(working);
        ranked.sort(Comparator
                .comparingDouble(WorkingParticipant::totalScore).reversed()
                .thenComparingLong(item -> deterministicTieBreaker(seed, item.input().participantId()))
                .thenComparing(item -> item.input().participantId()));

        double distance = parseDistanceMeters(distanceValue);
        double winnerSpeed = 15.0 + random.nextDouble() * 2.5;
        long winnerTime = Math.round(distance / winnerSpeed * 1000.0);
        long cumulativeGap = 0L;
        Map<Long, RankedTiming> timingByParticipant = new HashMap<>();
        for (int index = 0; index < ranked.size(); index++) {
            if (index > 0) {
                cumulativeGap += 250L + random.nextLong(951L);
            }
            timingByParticipant.put(ranked.get(index).input().participantId(),
                    new RankedTiming(index + 1, winnerTime + cumulativeGap));
        }

        return working.stream()
                .map(item -> toResult(item, timingByParticipant.get(item.input().participantId()), ranked.size()))
                .sorted(Comparator.comparingInt(SimulationResult::rank))
                .toList();
    }

    private WorkingParticipant buildParticipant(SimulationInput input, SplittableRandom random) {
        double historyScore = clamp((input.horseWinRate() + input.jockeyWinRate()) / 2.0);
        List<Double> cumulative = new ArrayList<>(TOTAL_TICKS + 1);
        cumulative.add(0.0);
        double distance = 0.0;
        for (int tick = 1; tick <= TOTAL_TICKS; tick++) {
            double multiplier = tick <= RANDOM_TICKS
                    ? 0.85 + random.nextDouble() * 0.30
                    : 0.85 + 0.30 * historyScore;
            distance += multiplier;
            cumulative.add(distance);
        }
        return new WorkingParticipant(input, historyScore, cumulative, distance);
    }

    private SimulationResult toResult(WorkingParticipant item, RankedTiming timing, int participantCount) {
        double finishAt = participantCount <= 1
                ? 0.90
                : 0.90 + ((timing.rank() - 1.0) / (participantCount - 1.0)) * 0.10;
        List<Checkpoint> checkpoints = new ArrayList<>(TOTAL_TICKS + 1);
        for (int tick = 0; tick <= TOTAL_TICKS; tick++) {
            double at;
            if (tick <= RANDOM_TICKS) {
                at = tick / (double) TOTAL_TICKS;
            } else {
                double secondHalfRatio = (tick - RANDOM_TICKS) / (double) RANDOM_TICKS;
                at = 0.5 + secondHalfRatio * (finishAt - 0.5);
            }
            double progress;
            if (tick == TOTAL_TICKS) {
                progress = 1.0;
            } else if (tick <= RANDOM_TICKS) {
                progress = item.cumulative().get(tick) / (RANDOM_TICKS * 1.15) * 0.5;
            } else {
                double halfProgress = item.cumulative().get(RANDOM_TICKS) / (RANDOM_TICKS * 1.15) * 0.5;
                double secondHalfDistance = item.cumulative().get(tick) - item.cumulative().get(RANDOM_TICKS);
                progress = halfProgress + secondHalfDistance / (RANDOM_TICKS * 1.15) * 0.5;
            }
            checkpoints.add(new Checkpoint(tick, round(at), round(progress)));
        }
        return new SimulationResult(item.input(), item.historyScore(), timing.rank(),
                timing.finishTimeMillis(), checkpoints);
    }

    public double parseDistanceMeters(String value) {
        String text = value == null ? "" : value.replace(",", "");
        Matcher matcher = DISTANCE_PATTERN.matcher(text);
        double parsed = matcher.find() ? Double.parseDouble(matcher.group(1)) : 1000.0;
        if (text.toLowerCase(Locale.ROOT).contains("km")) {
            parsed *= 1000.0;
        }
        return Double.isFinite(parsed) && parsed >= 200.0 ? parsed : 1000.0;
    }

    private long seedToLong(String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(seed).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private long deterministicTieBreaker(String seed, Long participantId) {
        return seedToLong(seed + ":" + participantId);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    public record SimulationInput(
            Long participantId,
            Long horseId,
            String horseName,
            Long jockeyId,
            String jockeyName,
            Integer gateNumber,
            long horseStarts,
            long horseWins,
            double horseWinRate,
            long jockeyStarts,
            long jockeyWins,
            double jockeyWinRate
    ) {
    }

    public record Checkpoint(int tick, double at, double progress) {
    }

    public record SimulationResult(
            SimulationInput input,
            double historyScore,
            int rank,
            long finishTimeMillis,
            List<Checkpoint> checkpoints
    ) {
    }

    private record WorkingParticipant(
            SimulationInput input,
            double historyScore,
            List<Double> cumulative,
            double totalScore
    ) {
    }

    private record RankedTiming(int rank, long finishTimeMillis) {
    }
}
