package com.minhthien.hoser_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceSettingsResponse {
    private BigDecimal jockeyHireTaxPercent;
    private BigDecimal betWinningTaxPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
