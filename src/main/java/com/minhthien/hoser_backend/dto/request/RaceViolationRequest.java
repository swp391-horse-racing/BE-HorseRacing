package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.RaceViolationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RaceViolationRequest {
    @NotNull(message = "Participant id is required")
    private Long participantId;

    @NotNull(message = "Violation type is required")
    private RaceViolationType type;

    @NotNull(message = "Violation severity is required")
    private RaceViolationSeverity severity;

    @NotBlank(message = "Violation description is required")
    @Size(max = 2000, message = "Violation description must be at most 2000 characters")
    private String description;

    @Size(max = 1000, message = "Penalty text must be at most 1000 characters")
    private String penaltyText;

    @NotNull(message = "Violation occurred time is required")
    private LocalDateTime occurredAt;
}
