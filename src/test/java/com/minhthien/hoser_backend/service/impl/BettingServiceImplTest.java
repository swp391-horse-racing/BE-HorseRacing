package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.BetMarketStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.FeatureDisabledException;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void bettableRacesRejectWithFeatureDisabledExceptionWhenBettingIsOff() {
        when(financeSettingsService.isBettingEnabled()).thenReturn(false);

        assertThrows(FeatureDisabledException.class, () -> service.getBettableRaceMarkets(1L));
    }

    @Test
    void enabledBettingReturnsAnEmptyListWhenThereAreNoOpenMarkets() {
        long userId = 2L;
        when(financeSettingsService.isBettingEnabled()).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).role(UserRole.SPECTATOR).build()));
        when(betMarketRepository.findByStatusOrderByRaceScheduledStartAtAsc(BetMarketStatus.OPEN))
                .thenReturn(List.of());

        assertEquals(List.of(), service.getBettableRaceMarkets(userId));
    }
}
