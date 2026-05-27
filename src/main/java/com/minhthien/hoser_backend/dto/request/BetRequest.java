package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BetRequest {
    @NotNull(message = "Participant id is required")
    private Long participantId;

    @NotNull(message = "Stake amount is required")
    @DecimalMin(value = "0.01", message = "Stake amount must be greater than zero")
    private BigDecimal stakeAmount;
}
