package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.JockeyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyProfileResponse {
    private Long id;
    private Long userId;
    private String username;
    private String fullName;
    private String licenseNumber;
    private Integer experienceYears;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String bio;
    private String awards;
    private String achievements;
    private String specialties;
    private String avatarUrl;
    private String licenseDocumentUrl;
    private JockeyStatus status;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
