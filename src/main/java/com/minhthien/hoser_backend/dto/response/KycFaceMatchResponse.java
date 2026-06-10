package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class KycFaceMatchResponse {
    private Long kycVerificationId;
    private Long profileId;
    private UserRole requestedRole;
    private KycStatus kycStatus;
    private RoleApprovalStatus applicationStatus;
    private BigDecimal faceScore;
}
