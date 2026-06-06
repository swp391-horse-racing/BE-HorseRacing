package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemRulesSettingsRequest {
    @NotBlank(message = "Default tournament rules are required")
    @Size(max = 10000, message = "Default tournament rules must not exceed 10000 characters")
    private String defaultTournamentRules;
}
