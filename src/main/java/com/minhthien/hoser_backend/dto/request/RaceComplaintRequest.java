package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RaceComplaintRequest {
    @NotNull(message = "Accused participant id is required")
    private Long accusedParticipantId;

    @Size(min = 1, max = 2000, message = "Complaint reason must be between 1 and 2000 characters")
    private String reason;

    @Size(max = 1000, message = "Evidence URL must be at most 1000 characters")
    private String evidenceUrl;
}
