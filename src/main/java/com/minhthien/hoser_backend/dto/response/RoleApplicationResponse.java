package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.KycStatus;
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
public class RoleApplicationResponse {
    private Long profileId;
    private Long userId;
    private String username;
    private String fullName;
    private UserRole role;
    private RoleApprovalStatus status;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String stableName;
    private String address;
    private String verificationDocumentUrl;

    private String displayName;
    private String phone;
    private String location;
    private String favoriteHorseBreed;

    private String licenseNumber;
    private Integer experienceYears;
    private String specialty;
    private String certificationDocumentUrl;

    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String bio;
    private String awards;
    private String achievements;
    private String specialties;
    private String avatarUrl;
    private String licenseDocumentUrl;

    private KycStatus kycStatus;
    private String idNumberMasked;
    private String kycFullName;
    private String dateOfBirth;
    private String gender;
    private String kycAddress;
    private String issueDate;
    private BigDecimal faceScore;
    private String cccdFrontImageUrl;
    private String cccdBackImageUrl;
    private String selfieImageUrl;
    private String kycRejectReason;
}
