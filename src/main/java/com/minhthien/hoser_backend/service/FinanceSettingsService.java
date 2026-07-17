package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.FinanceSettingsRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeShareSettingsRequest;
import com.minhthien.hoser_backend.dto.response.FinanceSettingsResponse;
import com.minhthien.hoser_backend.dto.response.RacePrizeShareSettingsResponse;
import com.minhthien.hoser_backend.entity.FinanceSettings;

import java.math.BigDecimal;

public interface FinanceSettingsService {
    FinanceSettings getOrCreateSettings();

    FinanceSettingsResponse getFinanceSettings();

    FinanceSettingsResponse updateFinanceSettings(FinanceSettingsRequest request, String updatedBy);

    BigDecimal getBetWinningTaxPercent();

    boolean isBettingEnabled();

    RacePrizeShareSettingsResponse getRacePrizeShareSettings();

    RacePrizeShareSettingsResponse updateRacePrizeShareSettings(RacePrizeShareSettingsRequest request,
                                                                String updatedBy);

    BigDecimal getRacePrizeJockeyPercent(Integer rank);
}
