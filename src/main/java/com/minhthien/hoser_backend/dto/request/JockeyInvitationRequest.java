package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class JockeyInvitationRequest {
    @NotNull(message = "Horse id is required")
    private Long horseId;

    @NotNull(message = "Race id is required")
    private Long raceId;

    @NotNull(message = "Jockey id is required")
    private Long jockeyId;

    @NotNull(message = "Remuneration amount is required")
    @DecimalMin(value = "0.00", message = "Remuneration amount must not be negative")
    private BigDecimal remunerationAmount;

    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
