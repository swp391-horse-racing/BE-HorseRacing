package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RaceSimulationConfirmRequest {
    @NotBlank(message = "Simulation run id is required")
    private String runId;
}
