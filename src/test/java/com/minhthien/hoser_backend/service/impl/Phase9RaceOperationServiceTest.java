package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceFinalizeResultRequest;
import com.minhthien.hoser_backend.dto.request.RaceParticipantCheckInRequest;
import com.minhthien.hoser_backend.dto.request.RaceResultEntryRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.Wallet;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletOwnerType;
import com.minhthien.hoser_backend.enums.WalletStatus;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase9RaceOperationServiceTest {
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
    void assignedRefereeCanCheckInParticipant() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(101L, race, 1, user(1L, "owner", UserRole.OWNER),
                user(2L, "jockey", UserRole.JOCKEY), RaceParticipantStatus.REGISTERED);
        RaceParticipantCheckInRequest request = new RaceParticipantCheckInRequest();
        request.setStatus(RaceParticipantStatus.CHECKED_IN);
        request.setNote("Ready");

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findById(101L)).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.save(participant)).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.checkInRaceParticipant(8L, 10L, 101L, request);

        assertThat(response.getStatus()).isEqualTo(RaceParticipantStatus.CHECKED_IN);
        assertThat(response.getCheckInNote()).isEqualTo("Ready");
        assertThat(response.getCheckedInBy()).isEqualTo(8L);
        assertThat(response.getCheckedInAt()).isNotNull();
    }

    @Test
    void nonAssignedRefereeCannotCheckInParticipant() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        Race race = race(user(9L, "other-referee", UserRole.REFEREE), RaceStatus.SCHEDULED);
        RaceParticipantCheckInRequest request = new RaceParticipantCheckInRequest();
        request.setStatus(RaceParticipantStatus.CHECKED_IN);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.checkInRaceParticipant(8L, 10L, 101L, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Referee is not assigned to this race");
    }

    @Test
    void assignedRefereeCanViewParticipantsBeforeCheckIn() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(referee, RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(101L, race, 1, owner, jockey, RaceParticipantStatus.REGISTERED);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(participant));

        var response = service.getRefereeRaceParticipants(8L, 10L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(101L);
        assertThat(response.get(0).getOwnerUsername()).isEqualTo("owner");
        assertThat(response.get(0).getJockeyUsername()).isEqualTo("jockey");
        assertThat(response.get(0).getStatus()).isEqualTo(RaceParticipantStatus.REGISTERED);
    }

    @Test
    void nonAssignedRefereeCannotViewParticipantsBeforeCheckIn() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        Race race = race(user(9L, "other-referee", UserRole.REFEREE), RaceStatus.SCHEDULED);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.getRefereeRaceParticipants(8L, 10L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Referee is not assigned to this race");
    }

    @Test
    void startRaceFailsWhenCheckedInBelowMinimum() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);
        race.setMinParticipants(2);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(participant(101L, race, 1, user(1L, "owner", UserRole.OWNER),
                        user(2L, "jockey", UserRole.JOCKEY), RaceParticipantStatus.CHECKED_IN)));

        assertThatThrownBy(() -> service.startRace(8L, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race does not have enough checked-in participants");
    }

    @Test
    void startRaceMovesScheduledRaceToOngoing() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(referee, RaceStatus.SCHEDULED);
        race.setMinParticipants(1);
        RaceParticipant checkedIn = participant(101L, race, 1, owner, jockey, RaceParticipantStatus.CHECKED_IN);
        RaceParticipant notCheckedIn = participant(102L, race, 2, owner, jockey, RaceParticipantStatus.REGISTERED);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(checkedIn, notCheckedIn));
        when(raceRepository.save(race)).thenAnswer(invocation -> invocation.getArgument(0));
        when(tournamentService.mapRace(race)).thenReturn(com.minhthien.hoser_backend.dto.response.RaceResponse.builder()
                .id(10L)
                .status(RaceStatus.ONGOING)
                .build());

        var response = service.startRace(8L, 10L);

        assertThat(response.getStatus()).isEqualTo(RaceStatus.ONGOING);
        assertThat(race.getStatus()).isEqualTo(RaceStatus.ONGOING);
        assertThat(checkedIn.getStatus()).isEqualTo(RaceParticipantStatus.CHECKED_IN);
        assertThat(notCheckedIn.getStatus()).isEqualTo(RaceParticipantStatus.ABSENT);
        assertThat(notCheckedIn.getCheckedInBy()).isEqualTo(8L);
        assertThat(notCheckedIn.getCheckInNote()).isEqualTo("Auto marked absent when race started");
        verify(raceParticipantRepository).save(notCheckedIn);
        verify(bettingService).lockRaceBets(10L);
    }

    @Test
    void finalizeResultFailsWhenRaceIsNotOngoing() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.finalizeRaceResult(8L, 10L, new RaceFinalizeResultRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only ongoing races can be finalized");
    }

    @Test
    void finalizeResultCreatesOfficialResultsAndPaysPrizeImmediately() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(referee, RaceStatus.ONGOING);
        race.replacePrizes(List.of(com.minhthien.hoser_backend.entity.RacePrize.builder()
                .rank(1)
                .amount(new BigDecimal("100000.00"))
                .build()));
        RaceParticipant participant = participant(101L, race, 1, owner, jockey, RaceParticipantStatus.CHECKED_IN);
        RaceFinalizeResultRequest request = resultRequest(101L);
        AtomicLong ids = new AtomicLong(501L);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(participant));
        when(financeSettingsService.getRacePrizeJockeyPercent(1)).thenReturn(new BigDecimal("20.00"));
        when(raceResultRepository.saveAll(any())).thenAnswer(invocation -> {
            List<RaceResult> results = invocation.getArgument(0);
            results.forEach(result -> result.setId(ids.getAndIncrement()));
            return results;
        });
        when(walletService.getOrCreateAdminWallet()).thenReturn(adminWallet(new BigDecimal("1000000.00")));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenAnswer(invocation -> {
            RaceResult result = RaceResult.builder()
                    .id(501L)
                    .race(race)
                    .participant(participant)
                    .owner(owner)
                    .horse(participant.getHorse())
                    .jockey(jockey)
                    .rank(1)
                    .status(RaceParticipantStatus.FINISHED)
                    .prizeAmount(new BigDecimal("100000.00"))
                    .ownerPrizeAmount(new BigDecimal("80000.00"))
                    .jockeyPrizeAmount(new BigDecimal("20000.00"))
                    .jockeyPrizePercent(new BigDecimal("20.00"))
                    .build();
            return List.of(result);
        });

        var response = service.finalizeRaceResult(8L, 10L, request);

        assertThat(response).hasSize(1);
        assertThat(race.getStatus()).isEqualTo(RaceStatus.RESULT_CONFIRMED);
        assertThat(participant.getStatus()).isEqualTo(RaceParticipantStatus.FINISHED);
        verify(walletService).debitAdmin(eq(new BigDecimal("100000.00")), eq(WalletTransactionType.PRIZE_PAYOUT),
                eq("RACE_RESULT"), eq("501"), eq("race-result:501:admin-prize-debit"), eq(null),
                eq("Race prize payout"));
        verify(walletService).credit(eq(1L), eq(new BigDecimal("80000.00")), eq(WalletTransactionType.PRIZE_PAYOUT),
                eq("RACE_RESULT"), eq("501"), eq("race-result:501:owner-prize-credit"), eq(null),
                eq("Race prize payout owner share"));
        verify(walletService).credit(eq(2L), eq(new BigDecimal("20000.00")), eq(WalletTransactionType.PRIZE_PAYOUT),
                eq("RACE_RESULT"), eq("501"), eq("race-result:501:jockey-prize-credit"), eq(null),
                eq("Race prize payout jockey share"));
        verify(bettingService).settleRaceBets(10L);
    }

    @Test
    void finalizeResultAutoAddsAbsentParticipantsMissingFromRequest() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(referee, RaceStatus.ONGOING);
        race.replacePrizes(List.of(com.minhthien.hoser_backend.entity.RacePrize.builder()
                .rank(1)
                .amount(new BigDecimal("100000.00"))
                .build()));
        RaceParticipant finisher = participant(101L, race, 1, owner, jockey, RaceParticipantStatus.CHECKED_IN);
        RaceParticipant absent = participant(102L, race, 2, owner, jockey, RaceParticipantStatus.ABSENT);
        absent.setCheckInNote("Auto marked absent when race started");
        RaceFinalizeResultRequest request = resultRequest(101L);
        AtomicLong ids = new AtomicLong(501L);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(finisher, absent));
        when(financeSettingsService.getRacePrizeJockeyPercent(1)).thenReturn(new BigDecimal("20.00"));
        when(raceResultRepository.saveAll(any())).thenAnswer(invocation -> {
            List<RaceResult> results = invocation.getArgument(0);
            results.forEach(result -> result.setId(ids.getAndIncrement()));
            assertThat(results).hasSize(2);
            assertThat(results.stream()
                    .anyMatch(result -> result.getParticipant().getId().equals(102L)
                            && result.getStatus() == RaceParticipantStatus.ABSENT
                            && result.getRank() == null
                            && result.getPrizeAmount().compareTo(BigDecimal.ZERO) == 0)).isTrue();
            return results;
        });
        when(walletService.getOrCreateAdminWallet()).thenReturn(adminWallet(new BigDecimal("1000000.00")));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenReturn(List.of(
                RaceResult.builder()
                        .id(501L)
                        .race(race)
                        .participant(finisher)
                        .owner(owner)
                        .horse(finisher.getHorse())
                        .jockey(jockey)
                        .rank(1)
                        .status(RaceParticipantStatus.FINISHED)
                        .prizeAmount(new BigDecimal("100000.00"))
                        .ownerPrizeAmount(new BigDecimal("80000.00"))
                        .jockeyPrizeAmount(new BigDecimal("20000.00"))
                        .jockeyPrizePercent(new BigDecimal("20.00"))
                        .build(),
                RaceResult.builder()
                        .id(502L)
                        .race(race)
                        .participant(absent)
                        .owner(owner)
                        .horse(absent.getHorse())
                        .jockey(jockey)
                        .status(RaceParticipantStatus.ABSENT)
                        .prizeAmount(BigDecimal.ZERO)
                        .build()));

        var response = service.finalizeRaceResult(8L, 10L, request);

        assertThat(response).hasSize(2);
        assertThat(absent.getStatus()).isEqualTo(RaceParticipantStatus.ABSENT);
        verify(bettingService).settleRaceBets(10L);
    }

    @Test
    void finalizeResultStillRejectsMissingCheckedInParticipant() {
        RaceDayServiceImpl service = service();
        User referee = user(8L, "referee", UserRole.REFEREE);
        User owner = user(1L, "owner", UserRole.OWNER);
        User jockey = user(2L, "jockey", UserRole.JOCKEY);
        Race race = race(referee, RaceStatus.ONGOING);
        RaceParticipant first = participant(101L, race, 1, owner, jockey, RaceParticipantStatus.CHECKED_IN);
        RaceParticipant second = participant(102L, race, 2, owner, jockey, RaceParticipantStatus.CHECKED_IN);

        when(userRepository.findById(8L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.finalizeRaceResult(8L, 10L, resultRequest(101L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race result must include every approved participant");
    }

    private RaceDayServiceImpl service() {
        return new RaceDayServiceImpl(raceRepository, raceRegistrationRepository, raceParticipantRepository,
                raceResultRepository, raceComplaintRepository, jockeyChallengeResultRepository,
                jockeyInvitationRepository, tournamentRepository, userRepository, walletService, tournamentService,
                financeSettingsService, mailService, bettingService);
    }

    private Race race(User referee, RaceStatus status) {
        return Race.builder()
                .id(10L)
                .tournament(Tournament.builder()
                        .id(20L)
                        .name("Summer Race Day")
                        .location("Ho Chi Minh City")
                        .status(TournamentStatus.SCHEDULED)
                        .build())
                .name("Sprint")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.of(2026, 6, 16, 9, 0))
                .scheduledEndAt(LocalDateTime.of(2026, 6, 16, 9, 30))
                .minParticipants(1)
                .maxParticipants(8)
                .referee(referee)
                .status(status)
                .build();
    }

    private RaceParticipant participant(Long id, Race race, int gateNumber, User owner, User jockey,
                                        RaceParticipantStatus status) {
        Horse horse = Horse.builder().id(id + 1000).name("Horse " + id).owner(owner).build();
        RaceRegistration registration = RaceRegistration.builder()
                .id(id + 2000)
                .race(race)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .build();
        return RaceParticipant.builder()
                .id(id)
                .race(race)
                .registration(registration)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .gateNumber(gateNumber)
                .status(status)
                .build();
    }

    private RaceFinalizeResultRequest resultRequest(Long participantId) {
        RaceResultEntryRequest entry = new RaceResultEntryRequest();
        entry.setParticipantId(participantId);
        entry.setRank(1);
        entry.setStatus(RaceParticipantStatus.FINISHED);
        entry.setFinishTimeMillis(60000L);
        RaceFinalizeResultRequest request = new RaceFinalizeResultRequest();
        request.setResults(List.of(entry));
        return request;
    }

    private Wallet adminWallet(BigDecimal amount) {
        return Wallet.builder()
                .id(900L)
                .ownerType(WalletOwnerType.ADMIN)
                .availableBalance(amount)
                .holdBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
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
