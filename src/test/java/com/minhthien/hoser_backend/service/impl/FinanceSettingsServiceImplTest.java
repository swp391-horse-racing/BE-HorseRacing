package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.FinanceSettingsRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeShareSettingRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeShareSettingsRequest;
import com.minhthien.hoser_backend.entity.FinanceSettings;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.FinanceSettingsRepository;
import com.minhthien.hoser_backend.repository.RacePrizeShareSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceSettingsServiceImplTest {
    @Mock
    private FinanceSettingsRepository financeSettingsRepository;

    @Mock
    private RacePrizeShareSettingRepository racePrizeShareSettingRepository;

    @Test
    void getFinanceSettingsCreatesDefaultWhenMissing() {
        FinanceSettingsServiceImpl service = service();
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        when(financeSettingsRepository.save(any(FinanceSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.getFinanceSettings();

        assertThat(response.getJockeyHireTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(response.getBetWinningTaxPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void updateFinanceSettingsNormalizesPercent() {
        FinanceSettingsServiceImpl service = service();
        FinanceSettings settings = FinanceSettings.builder()
                .id(FinanceSettings.SINGLETON_ID)
                .jockeyHireTaxPercent(new BigDecimal("10.00"))
                .betWinningTaxPercent(BigDecimal.ZERO)
                .build();
        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(financeSettingsRepository.save(any(FinanceSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateFinanceSettings(request("12.345"), "admin");

        assertThat(response.getJockeyHireTaxPercent()).isEqualByComparingTo("12.35");
        assertThat(settings.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateFinanceSettingsNormalizesBetWinningTaxPercent() {
        FinanceSettingsServiceImpl service = service();
        FinanceSettings settings = FinanceSettings.builder()
                .id(FinanceSettings.SINGLETON_ID)
                .jockeyHireTaxPercent(new BigDecimal("10.00"))
                .betWinningTaxPercent(BigDecimal.ZERO)
                .build();
        FinanceSettingsRequest request = new FinanceSettingsRequest();
        request.setBetWinningTaxPercent(new BigDecimal("7.555"));

        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(financeSettingsRepository.save(any(FinanceSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateFinanceSettings(request, "admin");

        assertThat(response.getJockeyHireTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(response.getBetWinningTaxPercent()).isEqualByComparingTo("7.56");
        assertThat(settings.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateFinanceSettingsKeepsExistingPercentWhenNotProvided() {
        FinanceSettingsServiceImpl service = service();
        FinanceSettings settings = FinanceSettings.builder()
                .id(FinanceSettings.SINGLETON_ID)
                .jockeyHireTaxPercent(new BigDecimal("10.00"))
                .betWinningTaxPercent(new BigDecimal("5.00"))
                .build();
        FinanceSettingsRequest request = new FinanceSettingsRequest();

        when(financeSettingsRepository.findById(FinanceSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(financeSettingsRepository.save(any(FinanceSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateFinanceSettings(request, "admin");

        assertThat(response.getJockeyHireTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(response.getBetWinningTaxPercent()).isEqualByComparingTo("5.00");
        assertThat(settings.getUpdatedBy()).isEqualTo("admin");
    }

    @Test
    void updateFinanceSettingsRejectsOutOfRangePercent() {
        FinanceSettingsServiceImpl service = service();

        assertThatThrownBy(() -> service.updateFinanceSettings(request("100.01"), "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Jockey hire tax percent must be between 0 and 100");

        verify(financeSettingsRepository, never()).save(any(FinanceSettings.class));
    }

    @Test
    void updateFinanceSettingsRejectsOutOfRangeBetWinningTaxPercent() {
        FinanceSettingsServiceImpl service = service();
        FinanceSettingsRequest request = new FinanceSettingsRequest();
        request.setBetWinningTaxPercent(new BigDecimal("100.01"));

        assertThatThrownBy(() -> service.updateFinanceSettings(request, "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Bet winning tax percent must be between 0 and 100");

        verify(financeSettingsRepository, never()).save(any(FinanceSettings.class));
    }

    @Test
    void updateRacePrizeShareSettingsCalculatesOwnerPercent() {
        FinanceSettingsServiceImpl service = service();
        when(racePrizeShareSettingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateRacePrizeShareSettings(shareRequest(
                share(1, "10"),
                share(2, "7.555")
        ), "admin");

        assertThat(response.getShares()).hasSize(2);
        assertThat(response.getShares().get(0).getRank()).isEqualTo(1);
        assertThat(response.getShares().get(0).getJockeyPercent()).isEqualByComparingTo("10.00");
        assertThat(response.getShares().get(0).getOwnerPercent()).isEqualByComparingTo("90.00");
        assertThat(response.getShares().get(1).getJockeyPercent()).isEqualByComparingTo("7.56");
        assertThat(response.getShares().get(1).getOwnerPercent()).isEqualByComparingTo("92.44");
        verify(racePrizeShareSettingRepository).deleteAllInBatch();
    }

    @Test
    void updateRacePrizeShareSettingsRejectsDuplicateRank() {
        FinanceSettingsServiceImpl service = service();

        assertThatThrownBy(() -> service.updateRacePrizeShareSettings(shareRequest(
                share(1, "10"),
                share(1, "12")
        ), "admin"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race prize share rank must be unique");

        verify(racePrizeShareSettingRepository, never()).saveAll(any());
    }

    @Test
    void getRacePrizeJockeyPercentDefaultsToZeroWhenMissing() {
        FinanceSettingsServiceImpl service = service();
        when(racePrizeShareSettingRepository.findByRank(1)).thenReturn(Optional.empty());

        assertThat(service.getRacePrizeJockeyPercent(1)).isEqualByComparingTo("0.00");
    }

    private FinanceSettingsRequest request(String percent) {
        FinanceSettingsRequest request = new FinanceSettingsRequest();
        request.setJockeyHireTaxPercent(new BigDecimal(percent));
        return request;
    }

    private FinanceSettingsServiceImpl service() {
        return new FinanceSettingsServiceImpl(financeSettingsRepository, racePrizeShareSettingRepository);
    }

    private RacePrizeShareSettingsRequest shareRequest(RacePrizeShareSettingRequest... shares) {
        RacePrizeShareSettingsRequest request = new RacePrizeShareSettingsRequest();
        request.setShares(List.of(shares));
        return request;
    }

    private RacePrizeShareSettingRequest share(int rank, String jockeyPercent) {
        RacePrizeShareSettingRequest request = new RacePrizeShareSettingRequest();
        request.setRank(rank);
        request.setJockeyPercent(new BigDecimal(jockeyPercent));
        return request;
    }
}
