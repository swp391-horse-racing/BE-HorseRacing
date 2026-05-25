package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RaceGateUpdateRequest {
    @NotNull(message = "Gate number is required")
    @Positive(message = "Gate number must be greater than zero")
    private Integer gateNumber;
}
