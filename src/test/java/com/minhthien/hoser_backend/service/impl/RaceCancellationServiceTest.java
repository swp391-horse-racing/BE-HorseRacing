package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.JockeyInvitationService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RefereeInvitationService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceCancellationServiceTest {
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private BettingService bettingService;
    @Mock
    private JockeyInvitationService jockeyInvitationService;
    @Mock
    private RefereeInvitationService refereeInvitationService;
    @Mock
    private RefereePaymentService refereePaymentService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MailService mailService;

    @InjectMocks
    private RaceCancellationService service;

    private User owner;
    private User jockey;
    private User referee;
    private Horse horse;
    private Tournament tournament;
    private Race race;
    private RaceRegistration registration;

    @BeforeEach
    void setUp() {
        owner = user(1L, "owner", UserRole.OWNER);
        jockey = user(2L, "jockey", UserRole.JOCKEY);
        referee = user(3L, "referee", UserRole.REFEREE);
        horse = Horse.builder()
                .id(20L)
                .name("Storm")
                .owner(owner)
                .status(HorseStatus.APPROVED)
                .build();
        tournament = Tournament.builder()
                .id(30L)
                .name("Summer Cup")
                .minTeams(2)
                .maxTeams(20)
                .build();
        race = Race.builder()
                .id(40L)
                .name("Qualifier")
                .tournament(tournament)
                .referee(referee)
                .status(RaceStatus.OPEN_REGISTRATION)
                .build();
        registration = RaceRegistration.builder()
                .id(50L)
                .race(race)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .entryFeeAmount(new BigDecimal("50000"))
                .status(RaceRegistrationStatus.APPROVED)
                .build();
    }

    @Test
    void cancellationRefundsAndReleasesEveryRaceAssignment() {
        when(raceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(race));
        when(raceRegistrationRepository.findByRaceIdOrderByCreatedAtDesc(40L))
                .thenReturn(List.of(registration));
        when(jockeyInvitationService.cancelActiveInvitationsForRace(40L, "Insufficient participants", "SYSTEM"))
                .thenReturn(List.of(jockey));
        when(refereeInvitationService.cancelActiveInvitationsForRace(40L, "Insufficient participants", "SYSTEM"))
                .thenReturn(List.of(referee));

        RaceCancellationService.RaceCancellationResult result = service.cancelRace(
                40L, null, "Insufficient participants", "SYSTEM", true);

        assertEquals(RaceStatus.CANCELLED, race.getStatus());
        assertNull(race.getReferee());
        assertEquals(RaceRegistrationStatus.CANCELLED, registration.getStatus());
        assertEquals(HorseStatus.APPROVED, horse.getStatus());
        assertEquals(List.of(owner, jockey, referee), result.affectedUsers());
        verify(walletService).refund(eq(owner.getId()), eq(new BigDecimal("50000")),
                eq("RACE_REGISTRATION"), eq("50"), anyString(), any(), anyString());
        verify(bettingService).cancelRaceBets(40L);
        verify(refereePaymentService).releaseForCancelledRace(null, race);
        verify(notificationService).notify(eq(owner), eq(NotificationType.REGISTRATION_CANCELLED),
                anyString(), anyString(), eq("RACE_REGISTRATION"), eq("50"), anyString());
        verify(notificationService).notify(eq(jockey), eq(NotificationType.RACE_CANCELLED),
                anyString(), anyString(), eq("RACE"), eq("40"), anyString());
        verify(notificationService).notify(eq(referee), eq(NotificationType.RACE_CANCELLED),
                anyString(), anyString(), eq("RACE"), eq("40"), anyString());
        verify(mailService).sendRegistrationCancelled(
                eq(owner), eq("Qualifier"), anyString(), eq("RACE_REGISTRATION"), eq("50"));
        verify(mailService).sendRaceCancelled(
                eq(jockey), eq("Qualifier"), anyString(), eq("RACE"), eq("40"));
        verify(mailService).sendRaceCancelled(
                eq(referee), eq("Qualifier"), anyString(), eq("RACE"), eq("40"));
    }

    @Test
    void alreadyCancelledRaceDoesNotRepeatFinancialOrNotificationSideEffects() {
        race.setStatus(RaceStatus.CANCELLED);
        when(raceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(race));

        RaceCancellationService.RaceCancellationResult result = service.cancelRace(
                40L, null, "Insufficient participants", "SYSTEM", true);

        assertEquals(List.of(), result.affectedUsers());
        verify(walletService, never()).refund(any(), any(), anyString(), anyString(), anyString(), any(), anyString());
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void tournamentCancellationNotifiesEachAffectedUserOnlyOnce() {
        service.notifyTournamentCancelledAfterCommit(
                tournament, List.of(owner, jockey, owner), 1L, 2);

        verify(notificationService, times(1)).notify(eq(owner), eq(NotificationType.TOURNAMENT_CANCELLED),
                anyString(), anyString(), eq("TOURNAMENT"), eq("30"), anyString());
        verify(notificationService, times(1)).notify(eq(jockey), eq(NotificationType.TOURNAMENT_CANCELLED),
                anyString(), anyString(), eq("TOURNAMENT"), eq("30"), anyString());
        verify(mailService, times(1)).sendTournamentCancelled(
                eq(owner), eq("Summer Cup"), anyString(), eq("TOURNAMENT"), eq("30"));
        verify(mailService, times(1)).sendTournamentCancelled(
                eq(jockey), eq("Summer Cup"), anyString(), eq("TOURNAMENT"), eq("30"));
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .build();
    }
}
