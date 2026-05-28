package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinanceSettingsRequest {
    @DecimalMin(value = "0.00", message = "Jockey hire tax percent must be at least 0")
    @DecimalMax(value = "100.00", message = "Jockey hire tax percent must be at most 100")
    private BigDecimal jockeyHireTaxPercent;

    @DecimalMin(value = "0.00", message = "Bet winning tax percent must be at least 0")
    @DecimalMax(value = "100.00", message = "Bet winning tax percent must be at most 100")
    private BigDecimal betWinningTaxPercent;
}
