package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String phone;
    private String email;
    private UserRole role;
    private UserRole pendingRole;
    private RoleApprovalStatus roleApprovalStatus;
    private String roleReviewReason;
    private String fullName;
    private Boolean twoFactorRequired;
    private String challengeId;
    private LocalDateTime challengeExpiresAt;
}
