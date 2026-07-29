package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.FinanceSettingsRequest;
import com.minhthien.hoser_backend.dto.response.FinanceSettingsResponse;
import com.minhthien.hoser_backend.entity.FinanceSettings;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.FinanceSettingsRepository;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceSettingsServiceImpl implements FinanceSettingsService {
    private static final BigDecimal MIN_PERCENT = new BigDecimal("0.00");
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100.00");

    private final FinanceSettingsRepository financeSettingsRepository;

    @Override
    @Transactional
    public FinanceSettings getOrCreateSettings() {
        return financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)
                .map(this::ensureSettingDefaults)
                .orElseGet(() -> {
                    int insertedRows = financeSettingsRepository.insertDefaultIfAbsent();
                    if (insertedRows > 0) {
                        log.info("Created default finance settings with id={}", FinanceSettings.SINGLETON_ID);
                    }
                    return financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)
                            .map(this::ensureSettingDefaults)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Unable to initialize finance settings with id="
                                            + FinanceSettings.SINGLETON_ID));
                });
    }

    @Override
    @Transactional
    public FinanceSettingsResponse getFinanceSettings() {
        return mapToResponse(getOrCreateSettings());
    }

    @Override
    @Transactional
    public FinanceSettingsResponse updateFinanceSettings(FinanceSettingsRequest request, String updatedBy) {
        if (request == null) {
            throw new BadRequestException("Finance settings request is required");
        }
        BigDecimal betWinningTaxPercent = request.getBetWinningTaxPercent() == null
                ? null
                : normalizePercent(request.getBetWinningTaxPercent(), "Bet winning tax percent");
        FinanceSettings settings = getOrCreateSettings();
        if (betWinningTaxPercent != null) {
            settings.setBetWinningTaxPercent(betWinningTaxPercent);
        }
        if (request.getBettingEnabled() != null) {
            settings.setBettingEnabled(request.getBettingEnabled());
        }
        settings.setUpdatedBy(updatedBy);
        return mapToResponse(financeSettingsRepository.save(settings));
    }

    @Override
    @Transactional
    public BigDecimal getBetWinningTaxPercent() {
        return getOrCreateSettings().getBetWinningTaxPercent();
    }

    @Override
    @Transactional
    public boolean isBettingEnabled() {
        return Boolean.TRUE.equals(getOrCreateSettings().getBettingEnabled());
    }

    private FinanceSettings ensureSettingDefaults(FinanceSettings settings) {
        boolean changed = false;
        if (settings.getBetWinningTaxPercent() == null) {
            settings.setBetWinningTaxPercent(FinanceSettings.DEFAULT_BET_WINNING_TAX_PERCENT);
            changed = true;
        }
        if (settings.getBettingEnabled() == null) {
            settings.setBettingEnabled(FinanceSettings.DEFAULT_BETTING_ENABLED);
            changed = true;
        }
        return changed ? financeSettingsRepository.save(settings) : settings;
    }

    private BigDecimal normalizePercent(BigDecimal percent, String label) {
        if (percent == null || percent.compareTo(MIN_PERCENT) < 0 || percent.compareTo(MAX_PERCENT) > 0) {
            throw new BadRequestException(label + " must be between 0 and 100");
        }
        return percent.setScale(2, RoundingMode.HALF_UP);
    }

    private FinanceSettingsResponse mapToResponse(FinanceSettings settings) {
        return FinanceSettingsResponse.builder()
                .betWinningTaxPercent(settings.getBetWinningTaxPercent())
                .bettingEnabled(settings.getBettingEnabled())
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

}
