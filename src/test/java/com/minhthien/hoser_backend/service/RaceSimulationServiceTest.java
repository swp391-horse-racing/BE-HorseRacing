package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceResultDraft;
import com.minhthien.hoser_backend.entity.RaceSimulation;
import com.minhthien.hoser_backend.entity.RaceSimulationParticipant;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceResultDraftStatus;
import com.minhthien.hoser_backend.enums.RaceSimulationStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.exception.ConflictException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultDraftRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.RaceSimulationRepository;
import com.minhthien.hoser_backend.repository.RaceViolationRepository;
import com.minhthien.hoser_backend.service.simulation.RaceSimulationEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceSimulationServiceTest {
    @Mock private RaceRepository raceRepository;
    @Mock private RaceParticipantRepository raceParticipantRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private RaceViolationRepository raceViolationRepository;
    @Mock private RaceSimulationRepository raceSimulationRepository;
    @Mock private RaceResultDraftRepository raceResultDraftRepository;
    @Mock private RaceSimulationEngine engine;
    @Mock private RealtimeEventService realtimeEventService;

    @InjectMocks
    private RaceSimulationService service;

    @Test
    void assignedRefereeIsRequired() {
        Race race = race(2L, RaceStatus.ONGOING);
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race));

        assertThrows(UnauthorizedException.class, () -> service.get(1L, 7L));
        verify(raceSimulationRepository, never()).findByRaceId(7L);
    }

    @Test
    void generateRequiresOngoingRace() {
        Race race = race(1L, RaceStatus.SCHEDULED);
        when(raceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(race));
        when(raceSimulationRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service.generate(1L, 7L));
        verify(raceParticipantRepository, never()).findByRaceIdOrderByGateNumberAsc(7L);
    }

    @Test
    void generateIsIdempotentWhenRunAlreadyExists() {
        Race race = race(1L, RaceStatus.ONGOING);
        RaceSimulation existing = simulation(race, LocalDateTime.now().minusSeconds(2));
        when(raceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(race));
        when(raceSimulationRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.of(existing));

        var response = service.generate(1L, 7L);

        assertEquals("run-1", response.getRunId());
        verify(raceSimulationRepository, never()).save(existing);
        verify(raceParticipantRepository, never()).findByRaceIdOrderByGateNumberAsc(7L);
    }

    @Test
    void confirmRejectsBeforePlaybackEnds() {
        Race race = race(1L, RaceStatus.ONGOING);
        RaceSimulation simulation = simulation(race, LocalDateTime.now());
        when(raceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(race));
        when(raceSimulationRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.of(simulation));
        when(raceResultDraftRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> service.confirm(1L, 7L, "run-1"));
        verify(raceResultDraftRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmCreatesReviewDraftWithoutPublishingOfficialResults() {
        Race race = race(1L, RaceStatus.ONGOING);
        RaceSimulation simulation = simulation(race, LocalDateTime.now().minusMinutes(1));
        RaceParticipant participant = RaceParticipant.builder()
                .id(11L)
                .race(race)
                .horse(Horse.builder().id(21L).name("Comet").build())
                .jockey(User.builder().id(31L).username("jockey-one").build())
                .gateNumber(1)
                .build();
        simulation.getParticipants().add(RaceSimulationParticipant.builder()
                .simulation(simulation)
                .participant(participant)
                .horseId(21L)
                .horseName("Comet")
                .jockeyId(31L)
                .jockeyName("jockey-one")
                .gateNumber(1)
                .horseStarts(0L)
                .horseWins(0L)
                .horseWinRate(0.5)
                .jockeyStarts(0L)
                .jockeyWins(0L)
                .jockeyWinRate(0.5)
                .historyScore(0.5)
                .rank(1)
                .finishTimeMillis(70_000L)
                .build());

        when(raceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(race));
        when(raceSimulationRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.of(simulation));
        when(raceResultDraftRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.empty());
        when(raceViolationRepository.findByRaceIdOrderByOccurredAtDesc(7L))
                .thenReturn(new ArrayList<>());

        var response = service.confirm(1L, 7L, "run-1");

        assertEquals(RaceStatus.ONGOING, race.getStatus());
        assertEquals(RaceSimulationStatus.DRAFTED, simulation.getStatus());
        assertEquals(RaceResultDraftStatus.REVIEW_PENDING,
                response.getResultDraft().getStatus());
        assertEquals(1L, response.getResultDraft().getVersion());
        assertEquals(1, response.getResultDraft().getRows().size());
        verify(raceResultDraftRepository).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(raceResultRepository);
    }

    @Test
    void staleDraftVersionIsRejected() {
        Race race = race(1L, RaceStatus.ONGOING);
        RaceResultDraft draft = RaceResultDraft.builder()
                .race(race)
                .simulation(simulation(race, LocalDateTime.now().minusMinutes(1)))
                .status(RaceResultDraftStatus.REVIEW_PENDING)
                .draftVersion(3L)
                .rows(new ArrayList<>())
                .build();
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race));
        when(raceResultDraftRepository.findByRaceIdForUpdate(7L)).thenReturn(Optional.of(draft));

        assertThrows(ConflictException.class,
                () -> service.prepareDraftFinalize(1L, 7L, 2L));
    }

    private Race race(Long refereeId, RaceStatus status) {
        return Race.builder()
                .id(7L)
                .referee(User.builder().id(refereeId).build())
                .status(status)
                .minParticipants(2)
                .build();
    }

    private RaceSimulation simulation(Race race, LocalDateTime generatedAt) {
        return RaceSimulation.builder()
                .race(race)
                .runId("run-1")
                .seed("seed-1")
                .status(RaceSimulationStatus.GENERATED)
                .playbackDurationMs(28_000L)
                .generatedAt(generatedAt)
                .playbackEndsAt(generatedAt.plusSeconds(28))
                .generatedBy(1L)
                .participants(new ArrayList<>())
                .build();
    }
}
