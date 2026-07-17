package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.FinanceSettingsRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeShareSettingRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeShareSettingsRequest;
import com.minhthien.hoser_backend.dto.response.FinanceSettingsResponse;
import com.minhthien.hoser_backend.dto.response.RacePrizeShareSettingResponse;
import com.minhthien.hoser_backend.dto.response.RacePrizeShareSettingsResponse;
import com.minhthien.hoser_backend.entity.FinanceSettings;
import com.minhthien.hoser_backend.entity.RacePrizeShareSetting;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.FinanceSettingsRepository;
import com.minhthien.hoser_backend.repository.RacePrizeShareSettingRepository;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceSettingsServiceImpl implements FinanceSettingsService {
    private static final BigDecimal MIN_PERCENT = new BigDecimal("0.00");
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100.00");

    private final FinanceSettingsRepository financeSettingsRepository;
    private final RacePrizeShareSettingRepository racePrizeShareSettingRepository;

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

    @Override
    @Transactional(readOnly = true)
    public RacePrizeShareSettingsResponse getRacePrizeShareSettings() {
        return mapRacePrizeShareSettings(racePrizeShareSettingRepository.findAllByOrderByRankAsc());
    }

    @Override
    @Transactional
    public RacePrizeShareSettingsResponse updateRacePrizeShareSettings(RacePrizeShareSettingsRequest request,
                                                                       String updatedBy) {
        if (request == null || request.getShares() == null) {
            throw new BadRequestException("Race prize share settings request is required");
        }
        Set<Integer> ranks = new HashSet<>();
        for (RacePrizeShareSettingRequest share : request.getShares()) {
            if (share.getRank() == null || share.getRank() <= 0) {
                throw new BadRequestException("Rank must be greater than zero");
            }
            if (!ranks.add(share.getRank())) {
                throw new BadRequestException("Race prize share rank must be unique");
            }
            normalizePercent(share.getJockeyPercent(), "Race prize jockey percent");
        }

        racePrizeShareSettingRepository.deleteAllInBatch();
        List<RacePrizeShareSetting> settings = request.getShares().stream()
                .map(share -> RacePrizeShareSetting.builder()
                        .rank(share.getRank())
                        .jockeyPercent(normalizePercent(share.getJockeyPercent(), "Race prize jockey percent"))
                        .createdBy(updatedBy)
                        .updatedBy(updatedBy)
                        .build())
                .toList();
        return mapRacePrizeShareSettings(racePrizeShareSettingRepository.saveAll(settings).stream()
                .sorted((left, right) -> left.getRank().compareTo(right.getRank()))
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getRacePrizeJockeyPercent(Integer rank) {
        if (rank == null) {
            return BigDecimal.ZERO;
        }
        return racePrizeShareSettingRepository.findByRank(rank)
                .map(RacePrizeShareSetting::getJockeyPercent)
                .orElse(BigDecimal.ZERO);
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

    private RacePrizeShareSettingsResponse mapRacePrizeShareSettings(List<RacePrizeShareSetting> settings) {
        return RacePrizeShareSettingsResponse.builder()
                .shares(settings.stream()
                        .map(this::mapRacePrizeShareSetting)
                        .toList())
                .build();
    }

    private RacePrizeShareSettingResponse mapRacePrizeShareSetting(RacePrizeShareSetting setting) {
        BigDecimal jockeyPercent = normalizePercent(setting.getJockeyPercent(), "Race prize jockey percent");
        return RacePrizeShareSettingResponse.builder()
                .rank(setting.getRank())
                .jockeyPercent(jockeyPercent)
                .ownerPercent(MAX_PERCENT.subtract(jockeyPercent).setScale(2, RoundingMode.HALF_UP))
                .build();
    }
}
