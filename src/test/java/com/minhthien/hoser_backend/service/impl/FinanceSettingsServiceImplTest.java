package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.FinanceSettings;
import com.minhthien.hoser_backend.repository.FinanceSettingsRepository;
import com.minhthien.hoser_backend.repository.RacePrizeShareSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceSettingsServiceImplTest {
    @Mock private FinanceSettingsRepository financeSettingsRepository;
    @Mock private RacePrizeShareSettingRepository racePrizeShareSettingRepository;

    @InjectMocks
    private FinanceSettingsServiceImpl service;

    @Test
    void newSettingsEnableBettingByDefault() {
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(financeSettingsRepository.save(any(FinanceSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertTrue(service.isBettingEnabled());
    }

    @Test
    void existingAdminDisabledSettingIsPreserved() {
        FinanceSettings settings = FinanceSettings.builder()
                .id(FinanceSettings.SINGLETON_ID)
                .betWinningTaxPercent(FinanceSettings.DEFAULT_BET_WINNING_TAX_PERCENT)
                .bettingEnabled(false)
                .build();
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID))
                .thenReturn(Optional.of(settings));

        assertFalse(service.isBettingEnabled());
        verify(financeSettingsRepository, never()).save(any(FinanceSettings.class));
    }
}
