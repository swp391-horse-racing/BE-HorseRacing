package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SystemFeesSettingsRequest {
    @NotNull(message = "Default registration fee is required")
    @DecimalMin(value = "0.00", message = "Default registration fee must not be negative")
    private BigDecimal defaultRegistrationFee;
}
