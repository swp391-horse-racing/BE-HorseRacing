package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BetMarketRequest {
    @NotNull(message = "Minimum stake is required")
    @DecimalMin(value = "0.01", message = "Minimum stake must be greater than zero")
    private BigDecimal minStake;

    @NotNull(message = "Maximum stake is required")
    @DecimalMin(value = "0.01", message = "Maximum stake must be greater than zero")
    private BigDecimal maxStake;

    @Size(max = 1000, message = "Bet market note must be at most 1000 characters")
    private String note;
}
