package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.ViolationResultAction;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ViolationPenaltyRuleResponse {
    private RaceViolationSeverity severity;
    private ViolationResultAction resultAction;
    private Long timePenaltyMillis;
}
