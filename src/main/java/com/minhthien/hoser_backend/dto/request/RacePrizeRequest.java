package com.minhthien.hoser_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RacePrizeRequest {
    @NotNull(message = "Prize rank is required")
    @Positive(message = "Prize rank must be greater than zero")
    @Schema(description = "Race result rank. Starts at 1.", example = "1")
    private Integer rank;

    @NotNull(message = "Prize amount is required")
    @PositiveOrZero(message = "Prize amount must not be negative")
    @Schema(description = "Prize amount paid to the winning horse owner wallet", example = "1000000")
    private BigDecimal amount = BigDecimal.ZERO;

    @Size(max = 255, message = "Prize item name must be at most 255 characters")
    private String itemName;

    @Size(max = 1000, message = "Prize note must be at most 1000 characters")
    private String note;
}
