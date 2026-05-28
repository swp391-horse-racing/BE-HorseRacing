package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.BetMarketRequest;
import com.minhthien.hoser_backend.dto.request.BetRequest;
import com.minhthien.hoser_backend.entity.Bet;
import com.minhthien.hoser_backend.entity.BetMarket;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.Wallet;
import com.minhthien.hoser_backend.enums.BetMarketStatus;
import com.minhthien.hoser_backend.enums.BetStatus;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletOwnerType;
import com.minhthien.hoser_backend.enums.WalletStatus;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.BetMarketRepository;
import com.minhthien.hoser_backend.repository.BetRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BettingServiceImplTest {
    @Mock
    private BetMarketRepository betMarketRepository;
    @Mock
    private BetRepository betRepository;
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceResultRepository raceResultRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private FinanceSettingsService financeSettingsService;

    @Test
    void adminCreatesAndOpensRaceBetMarket() {
        BettingServiceImpl service = service();
        enableBetting();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Race race = race(RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.DRAFT);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(participant));
        when(raceResultRepository.existsByRaceId(10L)).thenReturn(false);
        when(betMarketRepository.existsByRaceIdAndStatusIn(eq(10L), any())).thenReturn(false);
        when(betMarketRepository.save(any(BetMarket.class))).thenAnswer(invocation -> {
            BetMarket saved = invocation.getArgument(0);
            saved.setId(301L);
            return saved;
        });

        var created = service.createBetMarket(9L, 10L, marketRequest());

        assertThat(created.getId()).isEqualTo(301L);
        assertThat(created.getStatus()).isEqualTo(BetMarketStatus.DRAFT);

        when(betMarketRepository.findById(301L)).thenReturn(Optional.of(market));
        when(betMarketRepository.save(market)).thenReturn(market);

        var opened = service.openBetMarket(9L, 301L);

        assertThat(opened.getStatus()).isEqualTo(BetMarketStatus.OPEN);
        assertThat(market.getOpenedAt()).isNotNull();
    }

    @Test
    void spectatorPlacesBetAndStakeIsHeld() {
        BettingServiceImpl service = service();
        enableBetting();
        User spectator = user(5L, "spectator", UserRole.SPECTATOR);
        User admin = user(9L, "admin", UserRole.ADMIN);
        Race race = race(RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.OPEN);
        AtomicLong ids = new AtomicLong(401L);

        when(userRepository.findById(5L)).thenReturn(Optional.of(spectator));
        when(betMarketRepository.findByRaceIdAndStatus(10L, BetMarketStatus.OPEN)).thenReturn(Optional.of(market));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(participant));
        when(raceResultRepository.existsByRaceId(10L)).thenReturn(false);
        when(raceParticipantRepository.findById(101L)).thenReturn(Optional.of(participant));
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> {
            Bet bet = invocation.getArgument(0);
            if (bet.getId() == null) {
                bet.setId(ids.getAndIncrement());
            }
            return bet;
        });

        var response = service.placeBet(5L, 10L, betRequest(101L, "50000.00"));

        assertThat(response.getStatus()).isEqualTo(BetStatus.PLACED);
        assertThat(response.getPotentialPayoutAmount()).isEqualByComparingTo("100000.00");
        verify(walletService).hold(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("401"), eq("bet:401:stake-hold"), eq(null), eq("Bet stake held"));
    }

    @Test
    void nonSpectatorCannotPlaceBet() {
        BettingServiceImpl service = service();
        enableBetting();
        User owner = user(1L, "owner", UserRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.placeBet(1L, 10L, betRequest(101L, "50000.00")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only spectators can place bets");
    }

    @Test
    void stakeMustBeInsideAdminConfiguredLimits() {
        BettingServiceImpl service = service();
        enableBetting();
        User spectator = user(5L, "spectator", UserRole.SPECTATOR);
        User admin = user(9L, "admin", UserRole.ADMIN);
        Race race = race(RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.OPEN);

        when(userRepository.findById(5L)).thenReturn(Optional.of(spectator));
        when(betMarketRepository.findByRaceIdAndStatus(10L, BetMarketStatus.OPEN)).thenReturn(Optional.of(market));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(participant));
        when(raceResultRepository.existsByRaceId(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.placeBet(5L, 10L, betRequest(101L, "999999.00")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Stake amount exceeds market maximum");
        verify(walletService, never()).hold(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void userCanViewOnlyCurrentlyBettableRaceMarkets() {
        BettingServiceImpl service = service();
        enableBetting();
        User user = user(4L, "user", UserRole.USER);
        User admin = user(9L, "admin", UserRole.ADMIN);
        Race bettableRace = race(RaceStatus.SCHEDULED);
        Race ongoingRace = race(RaceStatus.ONGOING);
        ongoingRace.setId(11L);
        Race expiredRace = race(RaceStatus.SCHEDULED);
        expiredRace.setId(12L);
        expiredRace.setScheduledStartAt(LocalDateTime.now().minusMinutes(1));
        BetMarket bettableMarket = market(301L, bettableRace, admin, BetMarketStatus.OPEN);
        BetMarket ongoingMarket = market(302L, ongoingRace, admin, BetMarketStatus.OPEN);
        BetMarket expiredMarket = market(303L, expiredRace, admin, BetMarketStatus.OPEN);
        RaceParticipant participant = participant(101L, bettableRace);

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(betMarketRepository.findByStatusOrderByRaceScheduledStartAtAsc(BetMarketStatus.OPEN))
                .thenReturn(List.of(bettableMarket, ongoingMarket, expiredMarket));
        when(raceResultRepository.existsByRaceId(10L)).thenReturn(false);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L)).thenReturn(List.of(participant));

        var response = service.getBettableRaceMarkets(4L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(301L);
        assertThat(response.get(0).getRaceId()).isEqualTo(10L);
        assertThat(response.get(0).getOptions()).hasSize(1);
    }

    @Test
    void ownerCannotViewBettableRaceMarkets() {
        BettingServiceImpl service = service();
        enableBetting();
        User owner = user(1L, "owner", UserRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.getBettableRaceMarkets(1L))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only users or spectators can view bettable races");
    }

    @Test
    void bettingFlagOffBlocksOperationalBettingApis() {
        BettingServiceImpl service = service();
        when(financeSettingsService.isBettingEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.createBetMarket(9L, 10L, marketRequest()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Betting feature is disabled");
        assertThatThrownBy(() -> service.getPublicOpenBetMarket(10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Betting feature is disabled");
        assertThatThrownBy(() -> service.getBettableRaceMarkets(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Betting feature is disabled");
        assertThatThrownBy(() -> service.placeBet(5L, 10L, betRequest(101L, "50000.00")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Betting feature is disabled");

        verify(betMarketRepository, never()).save(any());
        verify(walletService, never()).hold(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void settleRaceBetsPaysWinnerAndCapturesLoserStake() {
        BettingServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User winnerUser = user(5L, "winner", UserRole.SPECTATOR);
        User loserUser = user(6L, "loser", UserRole.SPECTATOR);
        Race race = race(RaceStatus.RESULT_CONFIRMED);
        RaceParticipant winner = participant(101L, race);
        RaceParticipant loser = participant(102L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.CLOSED);
        Bet winningBet = bet(401L, market, winner, winnerUser, BetStatus.LOCKED, "50000.00");
        Bet losingBet = bet(402L, market, loser, loserUser, BetStatus.LOCKED, "30000.00");
        RaceResult winnerResult = RaceResult.builder()
                .race(race)
                .participant(winner)
                .status(RaceParticipantStatus.FINISHED)
                .rank(1)
                .build();

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(market));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenReturn(List.of(winnerResult));
        when(betRepository.findByRaceIdAndStatusIn(eq(10L), any())).thenReturn(List.of(winningBet, losingBet));
        when(financeSettingsService.getBetWinningTaxPercent()).thenReturn(BigDecimal.ZERO);
        when(walletService.getOrCreateAdminWallet()).thenReturn(adminWallet(new BigDecimal("1000000.00")));

        service.settleRaceBets(10L);

        assertThat(winningBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(losingBet.getStatus()).isEqualTo(BetStatus.LOST);
        verify(walletService).release(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("401"), eq("bet:401:stake-release"), eq(null), eq("Winning bet stake released"));
        verify(walletService).debitAdmin(eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_PAYOUT),
                eq("BET"), eq("401"), eq("bet:401:profit-admin-debit"), eq(null),
                eq("Winning bet profit paid"));
        verify(walletService).credit(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_PAYOUT),
                eq("BET"), eq("401"), eq("bet:401:profit-credit"), eq(null),
                eq("Winning bet profit received"));
        verify(walletService).capture(eq(6L), eq(new BigDecimal("30000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("402"), eq("bet:402:stake-capture"), eq(null),
                eq("Losing bet stake captured"));
        verify(walletService).creditAdmin(eq(new BigDecimal("30000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("402"), eq("bet:402:stake-admin-credit"), eq(null),
                eq("Losing bet stake received"));
        assertThat(winningBet.getWinningTaxPercent()).isEqualByComparingTo("0.00");
        assertThat(winningBet.getWinningTaxAmount()).isEqualByComparingTo("0.00");
        assertThat(winningBet.getGrossProfitAmount()).isEqualByComparingTo("50000.00");
        assertThat(winningBet.getNetProfitAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void settleRaceBetsTaxesWinningProfitOnly() {
        BettingServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User winnerUser = user(5L, "winner", UserRole.SPECTATOR);
        Race race = race(RaceStatus.RESULT_CONFIRMED);
        RaceParticipant winner = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.CLOSED);
        Bet winningBet = bet(401L, market, winner, winnerUser, BetStatus.LOCKED, "50000.00");
        RaceResult winnerResult = RaceResult.builder()
                .race(race)
                .participant(winner)
                .status(RaceParticipantStatus.FINISHED)
                .rank(1)
                .build();

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(market));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenReturn(List.of(winnerResult));
        when(betRepository.findByRaceIdAndStatusIn(eq(10L), any())).thenReturn(List.of(winningBet));
        when(financeSettingsService.getBetWinningTaxPercent()).thenReturn(new BigDecimal("10.00"));
        when(walletService.getOrCreateAdminWallet()).thenReturn(adminWallet(new BigDecimal("1000000.00")));

        service.settleRaceBets(10L);

        assertThat(winningBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(winningBet.getWinningTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(winningBet.getWinningTaxAmount()).isEqualByComparingTo("5000.00");
        assertThat(winningBet.getGrossProfitAmount()).isEqualByComparingTo("50000.00");
        assertThat(winningBet.getNetProfitAmount()).isEqualByComparingTo("45000.00");
        verify(walletService).release(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("401"), eq("bet:401:stake-release"), eq(null), eq("Winning bet stake released"));
        verify(walletService).debitAdmin(eq(new BigDecimal("45000.00")), eq(WalletTransactionType.BET_PAYOUT),
                eq("BET"), eq("401"), eq("bet:401:profit-admin-debit"), eq(null),
                eq("Winning bet profit paid"));
        verify(walletService).credit(eq(5L), eq(new BigDecimal("45000.00")), eq(WalletTransactionType.BET_PAYOUT),
                eq("BET"), eq("401"), eq("bet:401:profit-credit"), eq(null),
                eq("Winning bet profit received"));
    }

    @Test
    void hundredPercentWinningTaxOnlyReleasesStake() {
        BettingServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User winnerUser = user(5L, "winner", UserRole.SPECTATOR);
        Race race = race(RaceStatus.RESULT_CONFIRMED);
        RaceParticipant winner = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.CLOSED);
        Bet winningBet = bet(401L, market, winner, winnerUser, BetStatus.LOCKED, "50000.00");
        RaceResult winnerResult = RaceResult.builder()
                .race(race)
                .participant(winner)
                .status(RaceParticipantStatus.FINISHED)
                .rank(1)
                .build();

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(market));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenReturn(List.of(winnerResult));
        when(betRepository.findByRaceIdAndStatusIn(eq(10L), any())).thenReturn(List.of(winningBet));
        when(financeSettingsService.getBetWinningTaxPercent()).thenReturn(new BigDecimal("100.00"));

        service.settleRaceBets(10L);

        assertThat(winningBet.getStatus()).isEqualTo(BetStatus.WON);
        assertThat(winningBet.getWinningTaxAmount()).isEqualByComparingTo("50000.00");
        assertThat(winningBet.getNetProfitAmount()).isEqualByComparingTo("0.00");
        verify(walletService).release(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("401"), eq("bet:401:stake-release"), eq(null), eq("Winning bet stake released"));
        verify(walletService, never()).debitAdmin(any(), eq(WalletTransactionType.BET_PAYOUT),
                any(), any(), any(), any(), any());
        verify(walletService, never()).credit(any(), any(), eq(WalletTransactionType.BET_PAYOUT),
                any(), any(), any(), any(), any());
    }

    @Test
    void winningBetBecomesUnpaidWhenAdminWalletCannotPayProfit() {
        BettingServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User spectator = user(5L, "spectator", UserRole.SPECTATOR);
        Race race = race(RaceStatus.RESULT_CONFIRMED);
        RaceParticipant winner = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.CLOSED);
        Bet winningBet = bet(401L, market, winner, spectator, BetStatus.LOCKED, "50000.00");
        RaceResult winnerResult = RaceResult.builder()
                .race(race)
                .participant(winner)
                .status(RaceParticipantStatus.FINISHED)
                .rank(1)
                .build();

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(market));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenReturn(List.of(winnerResult));
        when(betRepository.findByRaceIdAndStatusIn(eq(10L), any())).thenReturn(List.of(winningBet));
        when(financeSettingsService.getBetWinningTaxPercent()).thenReturn(new BigDecimal("10.00"));
        when(walletService.getOrCreateAdminWallet()).thenReturn(adminWallet(BigDecimal.ZERO));

        service.settleRaceBets(10L);

        assertThat(winningBet.getStatus()).isEqualTo(BetStatus.UNPAID);
        assertThat(winningBet.getWinningTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(winningBet.getNetProfitAmount()).isEqualByComparingTo("45000.00");
        verify(walletService).release(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("401"), eq("bet:401:stake-release"), eq(null), eq("Winning bet stake released"));
        verify(walletService, never()).debitAdmin(any(), eq(WalletTransactionType.BET_PAYOUT),
                any(), any(), any(), any(), any());
        verify(walletService, never()).credit(any(), any(), eq(WalletTransactionType.BET_PAYOUT),
                any(), any(), any(), any(), any());
    }

    @Test
    void retryUnpaidBetUsesExistingWinningTaxSnapshot() {
        BettingServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User spectator = user(5L, "spectator", UserRole.SPECTATOR);
        Race race = race(RaceStatus.RESULT_CONFIRMED);
        RaceParticipant winner = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.SETTLED);
        Bet unpaidBet = bet(401L, market, winner, spectator, BetStatus.UNPAID, "50000.00");
        unpaidBet.setStakeReleaseKey("bet:401:stake-release");
        unpaidBet.setGrossProfitAmount(new BigDecimal("50000.00"));
        unpaidBet.setWinningTaxPercent(new BigDecimal("10.00"));
        unpaidBet.setWinningTaxAmount(new BigDecimal("5000.00"));
        unpaidBet.setNetProfitAmount(new BigDecimal("45000.00"));
        RaceResult winnerResult = RaceResult.builder()
                .race(race)
                .participant(winner)
                .status(RaceParticipantStatus.FINISHED)
                .rank(1)
                .build();

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(market));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(10L)).thenReturn(List.of(winnerResult));
        when(betRepository.findByRaceIdAndStatusIn(eq(10L), any())).thenReturn(List.of(unpaidBet));
        when(walletService.getOrCreateAdminWallet()).thenReturn(adminWallet(new BigDecimal("1000000.00")));

        service.settleRaceBets(10L);

        assertThat(unpaidBet.getStatus()).isEqualTo(BetStatus.WON);
        verify(financeSettingsService, never()).getBetWinningTaxPercent();
        verify(walletService, never()).release(any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService).debitAdmin(eq(new BigDecimal("45000.00")), eq(WalletTransactionType.BET_PAYOUT),
                eq("BET"), eq("401"), eq("bet:401:profit-admin-debit"), eq(null),
                eq("Winning bet profit paid"));
        verify(walletService).credit(eq(5L), eq(new BigDecimal("45000.00")), eq(WalletTransactionType.BET_PAYOUT),
                eq("BET"), eq("401"), eq("bet:401:profit-credit"), eq(null),
                eq("Winning bet profit received"));
    }

    @Test
    void cancelRaceBetsCancelsMarketAndReleasesOpenBets() {
        BettingServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User spectator = user(5L, "spectator", UserRole.SPECTATOR);
        Race race = race(RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(101L, race);
        BetMarket market = market(301L, race, admin, BetMarketStatus.OPEN);
        Bet placedBet = bet(401L, market, participant, spectator, BetStatus.PLACED, "50000.00");

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(10L), any()))
                .thenReturn(Optional.of(market));
        when(betRepository.findByRaceIdAndStatusIn(eq(10L), any())).thenReturn(List.of(placedBet));

        service.cancelRaceBets(10L);

        assertThat(market.getStatus()).isEqualTo(BetMarketStatus.CANCELLED);
        assertThat(market.getCancelledAt()).isNotNull();
        assertThat(placedBet.getStatus()).isEqualTo(BetStatus.CANCELLED);
        assertThat(placedBet.getSettledAt()).isNotNull();
        verify(walletService).release(eq(5L), eq(new BigDecimal("50000.00")), eq(WalletTransactionType.BET_STAKE),
                eq("BET"), eq("401"), eq("bet:401:stake-cancel-release"), eq(null),
                eq("Cancelled race bet stake released"));
    }

    private BettingServiceImpl service() {
        return new BettingServiceImpl(betMarketRepository, betRepository, raceRepository, raceParticipantRepository,
                raceResultRepository, userRepository, walletService, financeSettingsService);
    }

    private void enableBetting() {
        when(financeSettingsService.isBettingEnabled()).thenReturn(true);
    }

    private BetMarketRequest marketRequest() {
        BetMarketRequest request = new BetMarketRequest();
        request.setMinStake(new BigDecimal("10000.00"));
        request.setMaxStake(new BigDecimal("100000.00"));
        return request;
    }

    private BetRequest betRequest(Long participantId, String amount) {
        BetRequest request = new BetRequest();
        request.setParticipantId(participantId);
        request.setStakeAmount(new BigDecimal(amount));
        return request;
    }

    private BetMarket market(Long id, Race race, User admin, BetMarketStatus status) {
        return BetMarket.builder()
                .id(id)
                .race(race)
                .createdByAdmin(admin)
                .minStake(new BigDecimal("10000.00"))
                .maxStake(new BigDecimal("100000.00"))
                .status(status)
                .build();
    }

    private Bet bet(Long id, BetMarket market, RaceParticipant participant, User user, BetStatus status,
                    String stakeAmount) {
        BigDecimal stake = new BigDecimal(stakeAmount);
        return Bet.builder()
                .id(id)
                .market(market)
                .race(market.getRace())
                .participant(participant)
                .user(user)
                .stakeAmount(stake)
                .potentialPayoutAmount(stake.multiply(new BigDecimal("2.00")))
                .status(status)
                .build();
    }

    private Race race(RaceStatus status) {
        return Race.builder()
                .id(10L)
                .tournament(Tournament.builder()
                        .id(20L)
                        .name("Open Cup")
                        .location("Ho Chi Minh City")
                        .status(TournamentStatus.SCHEDULED)
                        .build())
                .name("Sprint")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.now().plusDays(1))
                .scheduledEndAt(LocalDateTime.now().plusDays(1).plusMinutes(30))
                .minParticipants(1)
                .maxParticipants(8)
                .status(status)
                .build();
    }

    private RaceParticipant participant(Long id, Race race) {
        User owner = user(id + 1000, "owner-" + id, UserRole.OWNER);
        User jockey = user(id + 2000, "jockey-" + id, UserRole.JOCKEY);
        Horse horse = Horse.builder()
                .id(id + 3000)
                .owner(owner)
                .name("Horse " + id)
                .build();
        return RaceParticipant.builder()
                .id(id)
                .race(race)
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .gateNumber(id.intValue())
                .status(RaceParticipantStatus.REGISTERED)
                .build();
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
