package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentStatusSchedulerTest {
    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private RegistrationOpenBroadcastService registrationOpenBroadcastService;

    @InjectMocks
    private TournamentStatusScheduler scheduler;

    @Test
    void opensPublishedTournamentWhenRegistrationOpenTimeHasPassed() {
        Tournament tournament = tournament(TournamentStatus.PUBLISHED);
        Race race = race(RaceStatus.PUBLISHED);
        tournament.getRaces().add(race);
        when(tournamentRepository.findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
                eq(TournamentStatus.PUBLISHED), any(LocalDateTime.class))).thenReturn(List.of(tournament));
        when(tournamentRepository.findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
                eq(TournamentStatus.OPEN_REGISTRATION), any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.updateRegistrationStatuses();

        assertEquals(TournamentStatus.OPEN_REGISTRATION, tournament.getStatus());
        assertEquals(RaceStatus.OPEN_REGISTRATION, race.getStatus());
        assertNotNull(tournament.getOpenedRegistrationAt());
        assertEquals("SYSTEM", tournament.getUpdatedBy());
        verify(tournamentRepository).save(tournament);
    }

    @Test
    void closesOpenTournamentWhenRegistrationCloseTimeHasPassed() {
        Tournament tournament = tournament(TournamentStatus.OPEN_REGISTRATION);
        Race race = race(RaceStatus.OPEN_REGISTRATION);
        tournament.getRaces().add(race);
        when(tournamentRepository.findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
                eq(TournamentStatus.PUBLISHED), any(LocalDateTime.class))).thenReturn(List.of());
        when(tournamentRepository.findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
                eq(TournamentStatus.OPEN_REGISTRATION), any(LocalDateTime.class))).thenReturn(List.of(tournament));

        scheduler.updateRegistrationStatuses();

        assertEquals(TournamentStatus.REGISTRATION_CLOSED, tournament.getStatus());
        assertEquals(RaceStatus.REGISTRATION_CLOSED, race.getStatus());
        assertEquals("SYSTEM", tournament.getUpdatedBy());
        verify(tournamentRepository).save(tournament);
    }

    @Test
    void catchesUpAfterRegistrationOpenTimeWasMissed() {
        Tournament tournament = tournament(TournamentStatus.PUBLISHED);
        tournament.setRegistrationOpenAt(LocalDateTime.now().minusHours(6));
        Race race = race(RaceStatus.PUBLISHED);
        tournament.getRaces().add(race);
        when(tournamentRepository.findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
                eq(TournamentStatus.PUBLISHED), any(LocalDateTime.class))).thenReturn(List.of(tournament));
        when(tournamentRepository.findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
                eq(TournamentStatus.OPEN_REGISTRATION), any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.updateRegistrationStatuses();

        assertEquals(TournamentStatus.OPEN_REGISTRATION, tournament.getStatus());
        assertEquals(RaceStatus.OPEN_REGISTRATION, race.getStatus());
        verify(tournamentRepository).save(tournament);
    }

    @Test
    void doesNotChangeCancelledRaceWhenOpeningRegistration() {
        Tournament tournament = tournament(TournamentStatus.PUBLISHED);
        Race activeRace = race(RaceStatus.PUBLISHED);
        Race cancelledRace = race(RaceStatus.CANCELLED);
        tournament.getRaces().addAll(List.of(activeRace, cancelledRace));
        when(tournamentRepository.findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
                eq(TournamentStatus.PUBLISHED), any(LocalDateTime.class))).thenReturn(List.of(tournament));
        when(tournamentRepository.findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
                eq(TournamentStatus.OPEN_REGISTRATION), any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.updateRegistrationStatuses();

        assertEquals(RaceStatus.OPEN_REGISTRATION, activeRace.getStatus());
        assertEquals(RaceStatus.CANCELLED, cancelledRace.getStatus());
        verify(tournamentRepository).save(tournament);
    }

    @Test
    void doesNotSaveWhenNoTournamentIsDue() {
        when(tournamentRepository.findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
                eq(TournamentStatus.PUBLISHED), any(LocalDateTime.class))).thenReturn(List.of());
        when(tournamentRepository.findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
                eq(TournamentStatus.OPEN_REGISTRATION), any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.updateRegistrationStatuses();

        verify(tournamentRepository, never()).save(any(Tournament.class));
    }

    private Tournament tournament(TournamentStatus status) {
        return Tournament.builder()
                .id(3L)
                .name("Summer Cup")
                .location("Ho Chi Minh City")
                .registrationOpenAt(LocalDateTime.now().minusDays(1))
                .registrationCloseAt(LocalDateTime.now().minusHours(1))
                .startAt(LocalDateTime.now().plusDays(5))
                .endAt(LocalDateTime.now().plusDays(6))
                .minTeams(1)
                .maxTeams(20)
                .status(status)
                .build();
    }

    private Race race(RaceStatus status) {
        return Race.builder()
                .id(10L)
                .name("Qualifier")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.now().plusDays(5))
                .scheduledEndAt(LocalDateTime.now().plusDays(5).plusHours(1))
                .minParticipants(1)
                .maxParticipants(8)
                .status(status)
                .build();
    }
}
