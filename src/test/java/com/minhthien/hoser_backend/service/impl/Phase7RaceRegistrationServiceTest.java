package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationReviewRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationWithdrawRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.JockeyChallengeResultRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase7RaceRegistrationServiceTest {
    private static final BigDecimal ENTRY_FEE = new BigDecimal("10000.00");

    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceResultRepository raceResultRepository;
    @Mock
    private RaceComplaintRepository raceComplaintRepository;
    @Mock
    private JockeyChallengeResultRepository jockeyChallengeResultRepository;
    @Mock
    private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private TournamentServiceImpl tournamentService;
    @Mock
    private FinanceSettingsService financeSettingsService;
    @Mock
    private MailService mailService;
    @Mock
    private BettingService bettingService;

    @Test
    void registerForRaceDebitsOwnerAndCreditsAdminImmediately() {
        RaceDayServiceImpl service = service();
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(ENTRY_FEE);
        JockeyInvitation invitation = acceptedInvitation(owner, jockey);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(raceRegistrationRepository.existsByRaceIdAndHorseIdAndStatusIn(eq(10L), eq(100L), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveHorseRegistrationWithinWindow(eq(100L), any(), any(), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveJockeyOverlap(eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.save(any(RaceRegistration.class))).thenAnswer(invocation -> {
            RaceRegistration registration = invocation.getArgument(0);
            if (registration.getId() == null) {
                registration.setId(70L);
            }
            return registration;
        });

        var response = service.registerForRace(1L, 10L, registrationRequest());

        assertThat(response.getStatus()).isEqualTo(RaceRegistrationStatus.PENDING);
        assertThat(response.getEntryFeeAmount()).isEqualByComparingTo("10000.00");
        verify(walletService).debit(eq(1L), eq(ENTRY_FEE), eq(WalletTransactionType.ENTRY_FEE),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-debit"),
                eq(null), eq("Race entry fee paid"));
        verify(walletService).creditAdmin(eq(ENTRY_FEE), eq(WalletTransactionType.ENTRY_FEE),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-admin-credit"),
                eq(null), eq("Race entry fee received"));
        verify(raceRegistrationRepository).existsActiveHorseRegistrationWithinWindow(
                eq(100L),
                eq(List.of(RaceRegistrationStatus.PENDING, RaceRegistrationStatus.APPROVED)),
                eq(LocalDateTime.of(2026, 6, 15, 10, 0)),
                eq(LocalDateTime.of(2026, 6, 17, 10, 0)));
    }

    @Test
    void registerForRaceWithZeroEntryFeeDoesNotTouchWallet() {
        RaceDayServiceImpl service = service();
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(BigDecimal.ZERO);
        JockeyInvitation invitation = acceptedInvitation(owner, jockey);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(raceRegistrationRepository.existsByRaceIdAndHorseIdAndStatusIn(eq(10L), eq(100L), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveHorseRegistrationWithinWindow(eq(100L), any(), any(), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveJockeyOverlap(eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.save(any(RaceRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerForRace(1L, 10L, registrationRequest());

        verify(walletService, never()).debit(any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).creditAdmin(any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).debitAdmin(any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).refund(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerForRacePropagatesInsufficientBalanceAndDoesNotFinalizeRegistration() {
        RaceDayServiceImpl service = service();
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(ENTRY_FEE);
        JockeyInvitation invitation = acceptedInvitation(owner, jockey);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(raceRegistrationRepository.existsByRaceIdAndHorseIdAndStatusIn(eq(10L), eq(100L), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveHorseRegistrationWithinWindow(eq(100L), any(), any(), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveJockeyOverlap(eq(2L), any(), any(), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.save(any(RaceRegistration.class))).thenAnswer(invocation -> {
            RaceRegistration registration = invocation.getArgument(0);
            registration.setId(70L);
            return registration;
        });
        when(walletService.debit(eq(1L), eq(ENTRY_FEE), eq(WalletTransactionType.ENTRY_FEE),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-debit"),
                eq(null), eq("Race entry fee paid")))
                .thenThrow(new BadRequestException("Wallet balance is insufficient"));

        assertThatThrownBy(() -> service.registerForRace(1L, 10L, registrationRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Wallet balance is insufficient");
        verify(walletService, never()).creditAdmin(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerForRaceRejectsHorseWithActiveRegistrationInsideTwentyFourHourWindow() {
        RaceDayServiceImpl service = service();
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(ENTRY_FEE);
        JockeyInvitation invitation = acceptedInvitation(owner, jockey);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(raceRegistrationRepository.existsByRaceIdAndHorseIdAndStatusIn(eq(10L), eq(100L), any()))
                .thenReturn(false);
        when(raceRegistrationRepository.existsActiveHorseRegistrationWithinWindow(eq(100L), any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.registerForRace(1L, 10L, registrationRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Horse can only join one race within a 24-hour period");

        verify(raceRegistrationRepository).existsActiveHorseRegistrationWithinWindow(
                eq(100L),
                eq(List.of(RaceRegistrationStatus.PENDING, RaceRegistrationStatus.APPROVED)),
                eq(LocalDateTime.of(2026, 6, 15, 10, 0)),
                eq(LocalDateTime.of(2026, 6, 17, 10, 0)));
        verify(raceRegistrationRepository, never()).existsActiveJockeyOverlap(any(), any(), any(), any());
        verify(raceRegistrationRepository, never()).save(any());
        verify(walletService, never()).debit(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void approveRaceRegistrationCreatesParticipantWithoutCapturingWalletFunds() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        RaceRegistration registration = pendingRegistration();
        RaceRegistrationReviewRequest request = new RaceRegistrationReviewRequest();
        request.setGateNumber(3);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceRegistrationRepository.findById(70L)).thenReturn(Optional.of(registration));
        when(raceParticipantRepository.existsByRaceIdAndGateNumber(10L, 3)).thenReturn(false);
        when(raceRegistrationRepository.save(any(RaceRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveRaceRegistration(9L, 70L, request);

        assertThat(response.getStatus()).isEqualTo(RaceRegistrationStatus.APPROVED);
        verify(raceParticipantRepository).save(any());
        verify(walletService, never()).capture(any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).creditAdmin(any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).debitAdmin(any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).refund(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectRaceRegistrationDebitsAdminAndRefundsOwner() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        RaceRegistration registration = pendingRegistration();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceRegistrationRepository.findById(70L)).thenReturn(Optional.of(registration));
        when(raceRegistrationRepository.save(any(RaceRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.rejectRaceRegistration(9L, 70L, null);

        assertThat(response.getStatus()).isEqualTo(RaceRegistrationStatus.REJECTED);
        verify(walletService).debitAdmin(eq(ENTRY_FEE), eq(WalletTransactionType.REFUND),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-admin-refund"),
                eq(null), eq("Race entry fee refunded after rejection"));
        verify(walletService).refund(eq(1L), eq(ENTRY_FEE),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-refund"),
                eq(null), eq("Race entry fee refunded after rejection"));
    }

    @Test
    void ownerWithdrawPendingRegistrationRefundsEntryFee() {
        RaceDayServiceImpl service = service();
        User owner = user(1L, "owner", UserRole.OWNER);
        RaceRegistration registration = pendingRegistration();
        RaceRegistrationWithdrawRequest request = new RaceRegistrationWithdrawRequest();
        request.setNote("Cannot attend");

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(raceRegistrationRepository.findById(70L)).thenReturn(Optional.of(registration));
        when(raceRegistrationRepository.save(any(RaceRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.withdrawRaceRegistration(1L, 70L, request);

        assertThat(response.getStatus()).isEqualTo(RaceRegistrationStatus.WITHDRAWN);
        assertThat(response.getWithdrawNote()).isEqualTo("Cannot attend");
        verify(walletService).debitAdmin(eq(ENTRY_FEE), eq(WalletTransactionType.REFUND),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-admin-refund"),
                eq(null), eq("Race entry fee refunded after owner withdrawal"));
        verify(walletService).refund(eq(1L), eq(ENTRY_FEE),
                eq("RACE_REGISTRATION"), eq("70"), eq("race-registration:70:entry-refund"),
                eq(null), eq("Race entry fee refunded after owner withdrawal"));
    }

    @Test
    void ownerCannotWithdrawAnotherOwnersRegistration() {
        RaceDayServiceImpl service = service();
        User otherOwner = user(3L, "other-owner", UserRole.OWNER);
        RaceRegistration registration = pendingRegistration();

        when(userRepository.findById(3L)).thenReturn(Optional.of(otherOwner));
        when(raceRegistrationRepository.findById(70L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.withdrawRaceRegistration(3L, 70L, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Cannot withdraw another owner's race registration");
        verify(walletService, never()).debitAdmin(any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).refund(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ownerCannotWithdrawNonPendingRegistration() {
        RaceDayServiceImpl service = service();
        User owner = user(1L, "owner", UserRole.OWNER);
        RaceRegistration registration = pendingRegistration();
        registration.setStatus(RaceRegistrationStatus.APPROVED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(raceRegistrationRepository.findById(70L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.withdrawRaceRegistration(1L, 70L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only pending race registrations can be withdrawn");

        verify(walletService, never()).refund(any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).debitAdmin(any(), any(), any(), any(), any(), any(), any());
    }

    private RaceDayServiceImpl service() {
        return new RaceDayServiceImpl(
                raceRepository,
                raceRegistrationRepository,
                raceParticipantRepository,
                raceResultRepository,
                raceComplaintRepository,
                jockeyChallengeResultRepository,
                jockeyInvitationRepository,
                tournamentRepository,
                userRepository,
                walletService,
                tournamentService,
                financeSettingsService,
                mailService,
                bettingService
        );
    }

    private RaceRegistrationRequest registrationRequest() {
        RaceRegistrationRequest request = new RaceRegistrationRequest();
        request.setHorseId(100L);
        request.setJockeyInvitationId(50L);
        request.setNote("Ready to race");
        return request;
    }

    private Race race(BigDecimal entryFee) {
        Tournament tournament = Tournament.builder()
                .id(20L)
                .name("Open Cup")
                .location("Ho Chi Minh City")
                .registrationOpenAt(LocalDateTime.of(2026, 6, 1, 9, 0))
                .registrationCloseAt(LocalDateTime.of(2026, 6, 10, 9, 0))
                .startAt(LocalDateTime.of(2026, 6, 16, 9, 0))
                .endAt(LocalDateTime.of(2026, 6, 17, 9, 0))
                .minTeams(2)
                .maxTeams(8)
                .status(TournamentStatus.OPEN_REGISTRATION)
                .build();
        return Race.builder()
                .id(10L)
                .tournament(tournament)
                .name("Sprint")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.of(2026, 6, 16, 10, 0))
                .scheduledEndAt(LocalDateTime.of(2026, 6, 16, 10, 30))
                .minParticipants(2)
                .maxParticipants(8)
                .entryFee(entryFee)
                .build();
    }

    private RaceRegistration pendingRegistration() {
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        JockeyInvitation invitation = acceptedInvitation(owner, jockey);
        return RaceRegistration.builder()
                .id(70L)
                .race(race(ENTRY_FEE))
                .owner(owner)
                .horse(invitation.getHorse())
                .jockey(jockey)
                .jockeyInvitation(invitation)
                .entryFeeAmount(ENTRY_FEE)
                .status(RaceRegistrationStatus.PENDING)
                .build();
    }

    private JockeyInvitation acceptedInvitation(User owner, User jockey) {
        Horse horse = Horse.builder()
                .id(100L)
                .owner(owner)
                .name("Lightning")
                .status(HorseStatus.APPROVED)
                .build();
        JockeyProfile profile = JockeyProfile.builder()
                .id(30L)
                .user(jockey)
                .licenseNumber("J-30")
                .hirePrice(BigDecimal.ONE)
                .status(JockeyStatus.APPROVED)
                .build();
        return JockeyInvitation.builder()
                .id(50L)
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .jockeyProfile(profile)
                .status(AssignmentStatus.ACCEPTED)
                .build();
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .active(true)
                .build();
    }
}
