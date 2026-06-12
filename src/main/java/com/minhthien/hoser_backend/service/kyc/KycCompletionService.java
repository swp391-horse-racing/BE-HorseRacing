package com.minhthien.hoser_backend.service.kyc;

import com.minhthien.hoser_backend.dto.kyc.FptFaceMatchResult;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KycCompletionService {
    private final KycVerificationRepository kycVerificationRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final RefereeProfileRepository refereeProfileRepository;
    private final SpectatorProfileRepository spectatorProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long complete(Long verificationId, Long userId, String selfieUrl, FptFaceMatchResult result) {
        KycVerification verification = kycVerificationRepository.findByIdAndUserId(verificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KycVerification", "id", verificationId));
        if (verification.getStatus() != KycStatus.OCR_PASSED) {
            throw new BadRequestException("Lần xác minh KYC này không còn hợp lệ");
        }
        verification.setSelfieImageUrl(selfieUrl);
        verification.setRawFaceResponse(result.rawResponse());
        verification.setFaceScore(result.similarity());
        verification.setFaceMatched(true);
        verification.setStatus(KycStatus.PASSED);
        verification.setRejectReason(null);

        User user = verification.getUser();
        Long profileId = switch (verification.getRequestedRole()) {
            case OWNER -> {
                OwnerProfile profile = ownerProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("OwnerProfile", "userId", userId));
                if (profile.getStatus() != RoleApprovalStatus.DRAFT) {
                    throw new BadRequestException("Hồ sơ OWNER không còn ở trạng thái DRAFT");
                }
                profile.setKycVerification(verification);
                profile.setStatus(RoleApprovalStatus.PENDING);
                profile.setUpdatedBy(user.getUsername());
                yield ownerProfileRepository.save(profile).getId();
            }
            case JOCKEY -> {
                JockeyProfile profile = jockeyProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", userId));
                if (profile.getStatus() != JockeyStatus.DRAFT) {
                    throw new BadRequestException("Hồ sơ JOCKEY không còn ở trạng thái DRAFT");
                }
                profile.setKycVerification(verification);
                profile.setStatus(JockeyStatus.PENDING);
                profile.setUpdatedBy(user.getUsername());
                yield jockeyProfileRepository.save(profile).getId();
            }
            case REFEREE -> {
                RefereeProfile profile = refereeProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("RefereeProfile", "userId", userId));
                if (profile.getStatus() != RoleApprovalStatus.DRAFT) {
                    throw new BadRequestException("Hồ sơ REFEREE không còn ở trạng thái DRAFT");
                }
                profile.setKycVerification(verification);
                profile.setStatus(RoleApprovalStatus.PENDING);
                profile.setUpdatedBy(user.getUsername());
                yield refereeProfileRepository.save(profile).getId();
            }
            case SPECTATOR -> {
                SpectatorProfile profile = spectatorProfileRepository.findByUserId(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("SpectatorProfile", "userId", userId));
                if (profile.getStatus() != RoleApprovalStatus.DRAFT) {
                    throw new BadRequestException("Hồ sơ SPECTATOR không còn ở trạng thái DRAFT");
                }
                profile.setKycVerification(verification);
                profile.setStatus(RoleApprovalStatus.PENDING);
                profile.setUpdatedBy(user.getUsername());
                yield spectatorProfileRepository.save(profile).getId();
            }
            default -> throw new BadRequestException("Role này không yêu cầu KYC");
        };

        user.setPendingRole(verification.getRequestedRole());
        user.setRoleApprovalStatus(RoleApprovalStatus.PENDING);
        user.setRoleReviewReason(null);
        user.setRoleReviewedBy(null);
        user.setRoleReviewedAt(null);
        user.setUpdatedBy(user.getUsername());
        userRepository.save(user);
        kycVerificationRepository.save(verification);
        return profileId;
    }
}
