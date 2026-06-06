package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.TwoFactorPolicy;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SystemSettingsResponse {
    private BigDecimal defaultRegistrationFee;
    private BigDecimal lateCheckInFee;
    private String defaultTournamentRules;
    private String registrationOpenEmailSubject;
    private String checkInReminderEmailSubject;
    private String raceResultEmailSubject;
    private TwoFactorPolicy twoFactorPolicy;
    private Integer sessionDurationMinutes;
    private String systemName;
    private String primaryColor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
