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
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BettingServiceImplTest {

    @Mock private BetMarketRepository betMarketRepository;
    @Mock private BetRepository betRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private RaceParticipantRepository raceParticipantRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private FinanceSettingsService financeSettingsService;

    @InjectMocks
    private BettingServiceImpl service;

    @Test
    void createsBetMarketWhenBettingIsEnabled() {
        User admin = User.builder().id(10L).username("admin").role(UserRole.ADMIN).build();
        User jockey = User.builder().id(20L).username("jockey").role(UserRole.JOCKEY).build();
        Tournament tournament = Tournament.builder().id(30L).name("Summer Cup").build();
        Race race = Race.builder()
                .id(40L)
                .name("Race 1")
                .tournament(tournament)
                .status(RaceStatus.SCHEDULED)
                .scheduledStartAt(LocalDateTime.now().plusHours(2))
                .build();
        RaceParticipant participant = RaceParticipant.builder()
                .id(50L)
                .race(race)
                .horse(Horse.builder().id(60L).name("Lightning").build())
                .jockey(jockey)
                .gateNumber(1)
                .status(RaceParticipantStatus.REGISTERED)
                .build();
        BetMarketRequest request = new BetMarketRequest();
        request.setMinStake(new BigDecimal("10000"));
        request.setMaxStake(new BigDecimal("100000"));

        when(financeSettingsService.isBettingEnabled()).thenReturn(true);
        when(financeSettingsService.getBetWinningTaxPercent()).thenReturn(new BigDecimal("10.00"));
        when(userRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(raceRepository.findById(40L)).thenReturn(Optional.of(race));
        when(raceResultRepository.existsByRaceId(40L)).thenReturn(false);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(40L))
                .thenReturn(List.of(participant));
        when(betMarketRepository.existsByRaceIdAndStatusIn(any(), anyList())).thenReturn(false);
        when(betMarketRepository.save(any(BetMarket.class))).thenAnswer(invocation -> {
            BetMarket market = invocation.getArgument(0);
            market.setId(70L);
            return market;
        });

        var response = service.createBetMarket(10L, 40L, request);

        assertEquals(70L, response.getId());
        assertEquals(40L, response.getRaceId());
        assertEquals(1, response.getOptions().size());
        assertEquals(new BigDecimal("10.00"), response.getWinningTaxPercent());
        verify(betMarketRepository).save(any(BetMarket.class));
    }

    @Test
    void placingBetStoresMarketTaxSnapshotAndReturnsNetPayoutEstimate() {
        User spectator = User.builder().id(11L).username("viewer").role(UserRole.SPECTATOR).build();
        User owner = User.builder().id(12L).username("owner").role(UserRole.OWNER).build();
        User jockey = User.builder().id(13L).username("jockey").role(UserRole.JOCKEY).build();
        Tournament tournament = Tournament.builder().id(30L).name("Summer Cup").build();
        Race race = Race.builder()
                .id(40L)
                .name("Race 1")
                .tournament(tournament)
                .status(RaceStatus.SCHEDULED)
                .scheduledStartAt(LocalDateTime.now().plusHours(2))
                .build();
        RaceParticipant participant = RaceParticipant.builder()
                .id(50L)
                .race(race)
                .owner(owner)
                .horse(Horse.builder().id(60L).name("Lightning").build())
                .jockey(jockey)
                .status(RaceParticipantStatus.REGISTERED)
                .build();
        BetMarket market = BetMarket.builder()
                .id(70L)
                .race(race)
                .createdByAdmin(User.builder().id(10L).username("admin").role(UserRole.ADMIN).build())
                .minStake(new BigDecimal("10000"))
                .maxStake(new BigDecimal("500000"))
                .winningTaxPercent(new BigDecimal("10.00"))
                .status(BetMarketStatus.OPEN)
                .build();
        BetRequest request = new BetRequest();
        request.setParticipantId(50L);
        request.setStakeAmount(new BigDecimal("100000.00"));

        when(financeSettingsService.isBettingEnabled()).thenReturn(true);
        when(userRepository.findById(11L)).thenReturn(Optional.of(spectator));
        when(betMarketRepository.findByRaceIdAndStatus(40L, BetMarketStatus.OPEN))
                .thenReturn(Optional.of(market));
        when(raceParticipantRepository.findById(50L)).thenReturn(Optional.of(participant));
        when(raceResultRepository.existsByRaceId(40L)).thenReturn(false);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(40L)).thenReturn(List.of(participant));
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> {
            Bet bet = invocation.getArgument(0);
            bet.setId(80L);
            return bet;
        });

        var response = service.placeBet(11L, 40L, request);

        assertEquals(new BigDecimal("10.00"), response.getWinningTaxPercent());
        assertEquals(new BigDecimal("10000.00"), response.getEstimatedWinningTaxAmount());
        assertEquals(new BigDecimal("190000.00"), response.getEstimatedNetPayoutAmount());
        verify(walletService).hold(eq(11L), eq(new BigDecimal("100000.00")), eq(
                        com.minhthien.hoser_backend.enums.WalletTransactionType.BET_STAKE),
                eq("BET"), eq("80"), eq("bet:80:stake-hold"), eq(null), eq("Bet stake held"));
    }

    @Test
    void settlementUsesBetSnapshotInsteadOfCurrentFinanceTax() {
        User spectator = User.builder().id(11L).username("viewer").role(UserRole.SPECTATOR).build();
        User owner = User.builder().id(12L).username("owner").role(UserRole.OWNER).build();
        User jockey = User.builder().id(13L).username("jockey").role(UserRole.JOCKEY).build();
        Tournament tournament = Tournament.builder().id(30L).name("Summer Cup").build();
        Race race = Race.builder()
                .id(40L)
                .name("Race 1")
                .tournament(tournament)
                .status(RaceStatus.RESULT_CONFIRMED)
                .build();
        RaceParticipant participant = RaceParticipant.builder()
                .id(50L)
                .race(race)
                .owner(owner)
                .horse(Horse.builder().id(60L).name("Lightning").build())
                .jockey(jockey)
                .status(RaceParticipantStatus.FINISHED)
                .build();
        BetMarket market = BetMarket.builder()
                .id(70L)
                .race(race)
                .createdByAdmin(User.builder().id(10L).username("admin").role(UserRole.ADMIN).build())
                .minStake(BigDecimal.ONE)
                .maxStake(new BigDecimal("500000"))
                .winningTaxPercent(new BigDecimal("10.00"))
                .status(BetMarketStatus.CLOSED)
                .build();
        Bet bet = Bet.builder()
                .id(80L)
                .market(market)
                .race(race)
                .participant(participant)
                .user(spectator)
                .stakeAmount(new BigDecimal("100000.00"))
                .potentialPayoutAmount(new BigDecimal("200000.00"))
                .winningTaxPercent(new BigDecimal("10.00"))
                .status(BetStatus.LOCKED)
                .build();
        RaceResult result = RaceResult.builder()
                .id(90L)
                .race(race)
                .participant(participant)
                .owner(owner)
                .horse(participant.getHorse())
                .jockey(jockey)
                .rank(1)
                .status(RaceParticipantStatus.FINISHED)
                .build();

        when(betMarketRepository.findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(eq(40L), anyList()))
                .thenReturn(Optional.of(market));
        when(raceRepository.findById(40L)).thenReturn(Optional.of(race));
        when(raceResultRepository.findByRaceIdOrderByRankAsc(40L)).thenReturn(List.of(result));
        when(betRepository.findByRaceIdAndStatusIn(eq(40L), anyList())).thenReturn(List.of(bet));
        when(walletService.getOrCreateAdminWallet()).thenReturn(Wallet.builder()
                .availableBalance(new BigDecimal("1000000.00"))
                .build());
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.settleRaceBets(40L);

        assertEquals(new BigDecimal("100000.00"), bet.getGrossProfitAmount());
        assertEquals(new BigDecimal("10000.00"), bet.getWinningTaxAmount());
        assertEquals(new BigDecimal("90000.00"), bet.getNetProfitAmount());
        assertEquals(BetStatus.WON, bet.getStatus());
        verify(financeSettingsService, never()).getBetWinningTaxPercent();
        verify(walletService).release(eq(11L), eq(new BigDecimal("100000.00")), any(), any(), any(), any(),
                any(), any());
        verify(walletService).debitAdmin(eq(new BigDecimal("90000.00")), any(), any(), any(), any(), any(), any());
        verify(walletService).credit(eq(11L), eq(new BigDecimal("90000.00")), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void rejectsBetMarketWithClearBusinessErrorWhenBettingIsDisabled() {
        BetMarketRequest request = new BetMarketRequest();
        request.setMinStake(BigDecimal.ONE);
        request.setMaxStake(BigDecimal.TEN);
        when(financeSettingsService.isBettingEnabled()).thenReturn(false);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.createBetMarket(10L, 40L, request));

        assertEquals("Betting feature is disabled", error.getMessage());
        verify(betMarketRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }
}
