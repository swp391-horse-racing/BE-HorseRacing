package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KycOcrResponse {
    private Long kycVerificationId;
    private UserRole requestedRole;
    private KycStatus kycStatus;
    private String idNumberMasked;
    private String fullName;
    private String dateOfBirth;
    private String gender;
    private String address;
    private String issueDate;
}
