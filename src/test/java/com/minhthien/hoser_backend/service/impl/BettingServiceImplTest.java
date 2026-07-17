package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.BetMarketRequest;
import com.minhthien.hoser_backend.entity.BetMarket;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
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
        verify(betMarketRepository).save(any(BetMarket.class));
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
