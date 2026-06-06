package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.TwoFactorPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SystemSecuritySettingsRequest {
    @NotNull(message = "Two-factor policy is required")
    private TwoFactorPolicy twoFactorPolicy;

    @NotNull(message = "Session duration is required")
    @Min(value = 5, message = "Session duration must be at least 5 minutes")
    @Max(value = 1440, message = "Session duration must not exceed 1440 minutes")
    private Integer sessionDurationMinutes;
}
