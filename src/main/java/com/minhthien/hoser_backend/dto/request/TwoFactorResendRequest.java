package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TwoFactorResendRequest {
    @NotBlank(message = "Challenge ID is required")
    private String challengeId;
}
