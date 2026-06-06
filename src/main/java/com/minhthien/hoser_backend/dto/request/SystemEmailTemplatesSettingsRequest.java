package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemEmailTemplatesSettingsRequest {
    @NotBlank(message = "Registration open email subject is required")
    @Size(max = 300, message = "Email subject must not exceed 300 characters")
    private String registrationOpenEmailSubject;

    @NotBlank(message = "Check-in reminder email subject is required")
    @Size(max = 300, message = "Email subject must not exceed 300 characters")
    private String checkInReminderEmailSubject;

    @NotBlank(message = "Race result email subject is required")
    @Size(max = 300, message = "Email subject must not exceed 300 characters")
    private String raceResultEmailSubject;
}
