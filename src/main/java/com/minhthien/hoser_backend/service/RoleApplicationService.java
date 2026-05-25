package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.OwnerRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.RefereeRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.SpectatorRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.response.RoleApplicationResponse;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RoleApplicationService {
    RoleApplicationResponse submitOwnerApplication(Long userId, OwnerRoleApplicationRequest request,
                                                   MultipartFile verificationDocument);

    RoleApplicationResponse submitJockeyApplication(Long userId, JockeyProfileRequest request,
                                                    MultipartFile avatar, MultipartFile licenseDocument);

    RoleApplicationResponse submitSpectatorApplication(Long userId, SpectatorRoleApplicationRequest request);

    RoleApplicationResponse submitRefereeApplication(Long userId, RefereeRoleApplicationRequest request,
                                                     MultipartFile certificationDocument);

    RoleApplicationResponse getMyApplication(Long userId);

    List<RoleApplicationResponse> getAdminApplications(UserRole role, RoleApprovalStatus status);

    RoleApplicationResponse approveApplication(Long profileId, Long adminId);

    RoleApplicationResponse approveApplication(Long profileId, Long adminId, UserRole role);

    RoleApplicationResponse rejectApplication(Long profileId, Long adminId, AdminReviewRequest request);

    RoleApplicationResponse rejectApplication(Long profileId, Long adminId, UserRole role, AdminReviewRequest request);
}
