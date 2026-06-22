package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefereeInvitationRequest {
    @NotNull(message = "Race id is required")
    private Long raceId;

    @NotNull(message = "Referee id is required")
    private Long refereeId;

    @NotNull(message = "Salary config id is required")
    private Long salaryConfigId;

    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
