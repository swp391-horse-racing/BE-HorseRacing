package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class SystemRaceDistancesSettingsRequest {
    @NotEmpty(message = "Race distances are required")
    private List<@NotNull(message = "Race distance is required")
    @Positive(message = "Race distance must be greater than zero") Integer> distancesMeters;
}
