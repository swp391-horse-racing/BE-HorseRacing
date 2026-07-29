package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.FinanceSettingsRequest;
import com.minhthien.hoser_backend.entity.FinanceSettings;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.FinanceSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceSettingsServiceImplTest {

    @Mock
    private FinanceSettingsRepository financeSettingsRepository;

    @InjectMocks
    private FinanceSettingsServiceImpl service;

    @Test
    void emptyTableCreatesEnabledDefaultSettings() {
        FinanceSettings defaults = defaultSettings(true);
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(defaults));
        when(financeSettingsRepository.insertDefaultIfAbsent()).thenReturn(1);

        FinanceSettings result = service.getOrCreateSettings();

        assertEquals(FinanceSettings.SINGLETON_ID, result.getId());
        assertEquals(new BigDecimal("10.00"), result.getBetWinningTaxPercent());
        assertTrue(result.getBettingEnabled());
        assertEquals("system", result.getCreatedBy());
        assertEquals("system", result.getUpdatedBy());
        verify(financeSettingsRepository).insertDefaultIfAbsent();
    }

    @Test
    void existingEnabledSettingsAllowsBettingWithoutInsert() {
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID))
                .thenReturn(Optional.of(defaultSettings(true)));

        assertTrue(service.isBettingEnabled());
        verify(financeSettingsRepository, never()).insertDefaultIfAbsent();
    }

    @Test
    void existingDisabledSettingsDisablesBettingWithoutBeingOverwritten() {
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID))
                .thenReturn(Optional.of(defaultSettings(false)));

        assertFalse(service.isBettingEnabled());
        verify(financeSettingsRepository, never()).insertDefaultIfAbsent();
        verify(financeSettingsRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsTaxPercentOutsideZeroToOneHundred() {
        FinanceSettingsRequest belowMinimum = new FinanceSettingsRequest();
        belowMinimum.setBetWinningTaxPercent(new BigDecimal("-0.01"));
        FinanceSettingsRequest aboveMaximum = new FinanceSettingsRequest();
        aboveMaximum.setBetWinningTaxPercent(new BigDecimal("100.01"));

        assertThrows(BadRequestException.class,
                () -> service.updateFinanceSettings(belowMinimum, "admin"));
        assertThrows(BadRequestException.class,
                () -> service.updateFinanceSettings(aboveMaximum, "admin"));
        verify(financeSettingsRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private FinanceSettings defaultSettings(boolean enabled) {
        LocalDateTime now = LocalDateTime.now();
        return FinanceSettings.builder()
                .id(FinanceSettings.SINGLETON_ID)
                .betWinningTaxPercent(FinanceSettings.DEFAULT_BET_WINNING_TAX_PERCENT)
                .bettingEnabled(enabled)
                .createdAt(now)
                .updatedAt(now)
                .createdBy(FinanceSettings.DEFAULT_AUDIT_USER)
                .updatedBy(FinanceSettings.DEFAULT_AUDIT_USER)
                .build();
    }
}
