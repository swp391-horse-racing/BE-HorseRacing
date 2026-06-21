package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RaceRefereeAssignmentRequest {
    @NotNull(message = "Referee id is required")
    private Long refereeId;

    @NotNull(message = "Salary config id is required")
    private Long salaryConfigId;
}
