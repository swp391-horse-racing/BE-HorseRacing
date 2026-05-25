package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RaceParticipantCheckInRequest {
    @NotNull(message = "Check-in status is required")
    private RaceParticipantStatus status;

    @Size(max = 1000, message = "Check-in note must be at most 1000 characters")
    private String note;
}
