package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.ViolationResultAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class ViolationPenaltyRuleRequest {
    @NotNull(message = "Violation severity is required")
    private RaceViolationSeverity severity;

    @NotNull(message = "Violation result action is required")
    private ViolationResultAction resultAction;

    @PositiveOrZero(message = "Time penalty must not be negative")
    private Long timePenaltyMillis = 0L;
}
