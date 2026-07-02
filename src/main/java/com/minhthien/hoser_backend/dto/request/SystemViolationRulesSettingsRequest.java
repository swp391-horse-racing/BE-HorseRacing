package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SystemViolationRulesSettingsRequest {
    @Valid
    @NotEmpty(message = "Violation penalty rules are required")
    private List<ViolationPenaltyRuleRequest> rules;
}
