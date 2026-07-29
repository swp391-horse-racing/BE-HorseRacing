package com.minhthien.hoser_backend.service.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceSimulationEngineTest {
    private final RaceSimulationEngine engine = new RaceSimulationEngine();

    @Test
    void sameSeedProducesSameResultAndUniqueRanks() {
        List<RaceSimulationEngine.SimulationInput> inputs = inputs(0.8, 0.5, 0.2);

        var first = engine.simulate(inputs, "fixed-seed", "1200m");
        var second = engine.simulate(inputs, "fixed-seed", "1200m");

        assertEquals(first, second);
        assertEquals(List.of(1, 2, 3), first.stream().map(RaceSimulationEngine.SimulationResult::rank).toList());
        assertEquals(3, first.stream().map(item -> item.input().participantId()).distinct().count());
        assertTrue(first.get(1).finishTimeMillis() > first.get(0).finishTimeMillis());
        assertTrue(first.get(2).finishTimeMillis() > first.get(1).finishTimeMillis());
    }

    @Test
    void firstHalfCheckpointsDoNotDependOnHistory() {
        var neutral = engine.simulate(inputs(0.5, 0.5, 0.5), "same-random-half", "1000m");
        var changed = engine.simulate(inputs(0.95, 0.1, 0.7), "same-random-half", "1000m");

        for (long participantId = 1; participantId <= 3; participantId++) {
            var first = byParticipant(neutral, participantId);
            var second = byParticipant(changed, participantId);
            for (int tick = 0; tick <= RaceSimulationEngine.RANDOM_TICKS; tick++) {
                assertEquals(first.checkpoints().get(tick).progress(),
                        second.checkpoints().get(tick).progress());
            }
        }
    }

    @Test
    void strongerHistoryProducesMoreSecondHalfProgress() {
        var results = engine.simulate(inputs(1.0, 0.5, 0.0), "history-half", "1.5km");
        var strong = byParticipant(results, 1L);
        var weak = byParticipant(results, 3L);

        double strongGain = strong.checkpoints().get(27).progress()
                - strong.checkpoints().get(14).progress();
        double weakGain = weak.checkpoints().get(27).progress()
                - weak.checkpoints().get(14).progress();
        assertTrue(strongGain > weakGain);
        results.forEach(result -> assertEquals(1.0,
                result.checkpoints().get(result.checkpoints().size() - 1).progress()));
    }

    @Test
    void parsesMetersAndKilometersWithFallback() {
        assertEquals(1200.0, engine.parseDistanceMeters("1200m"));
        assertEquals(1500.0, engine.parseDistanceMeters("1.5 km"));
        assertEquals(1000.0, engine.parseDistanceMeters("unknown"));
    }

    private RaceSimulationEngine.SimulationResult byParticipant(
            List<RaceSimulationEngine.SimulationResult> results, Long participantId) {
        return results.stream()
                .filter(item -> item.input().participantId().equals(participantId))
                .findFirst()
                .orElseThrow();
    }

    private List<RaceSimulationEngine.SimulationInput> inputs(double first, double second, double third) {
        return List.of(
                input(1L, first),
                input(2L, second),
                input(3L, third)
        );
    }

    private RaceSimulationEngine.SimulationInput input(Long id, double rate) {
        return new RaceSimulationEngine.SimulationInput(
                id, id * 10, "Horse " + id, id * 100, "Jockey " + id, id.intValue(),
                10, Math.round(rate * 10), rate,
                10, Math.round(rate * 10), rate
        );
    }
}
