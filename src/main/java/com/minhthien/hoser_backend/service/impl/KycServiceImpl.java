package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.kyc.FptFaceMatchResult;
import com.minhthien.hoser_backend.dto.kyc.FptOcrResult;
import com.minhthien.hoser_backend.dto.response.KycFaceMatchResponse;
import com.minhthien.hoser_backend.dto.response.KycOcrResponse;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.KycService;
import com.minhthien.hoser_backend.service.kyc.FptAiClient;
import com.minhthien.hoser_backend.service.kyc.KycFailurePersistenceService;
import com.minhthien.hoser_backend.service.kyc.KycCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final RefereeProfileRepository refereeProfileRepository;
    private final KycVerificationRepository kycVerificationRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final FptAiClient fptAiClient;
    private final KycFailurePersistenceService failurePersistenceService;
    private final KycCompletionService completionService;

    @Override
    public KycOcrResponse verifyCccd(Long userId, UserRole requestedRole,
                                     MultipartFile cccdFront, MultipartFile cccdBack) {
        User user = requireUser(userId);
        requireDraft(userId, requestedRole);
        validateImage(cccdFront, "CCCD mặt trước");
        validateImage(cccdBack, "CCCD mặt sau");

        String folder = "hoser/kyc/" + userId;
        String frontUrl = cloudinaryUploadService.uploadImage(cccdFront, folder);
        String backUrl = cloudinaryUploadService.uploadImage(cccdBack, folder);
        FptOcrResult result = fptAiClient.callOcr(cccdFront);
        KycVerification verification = KycVerification.builder()
                .user(user)
                .requestedRole(requestedRole)
                .status(result.passed() ? KycStatus.OCR_PASSED : KycStatus.FAILED)
                .frontOcrPassed(result.passed())
                .frontImageUrl(frontUrl)
                .backImageUrl(backUrl)
                .rawFrontResponse(result.rawResponse())
                .rejectReason(result.rejectReason())
                .build();

        if (!result.passed()) {
            KycVerification failed = failurePersistenceService.save(verification);
            throw new BadRequestException("KYC thất bại: " + failed.getRejectReason());
        }

        String normalizedId = normalizeIdNumber(result.idNumber());
        String idHash = sha256(normalizedId);
        verification.setIdNumberHash(idHash);
        verification.setIdNumberMasked(maskIdNumber(normalizedId));
        verification.setFullName(result.fullName());
        verification.setDateOfBirth(result.dateOfBirth());
        verification.setGender(result.gender());
        verification.setAddress(result.address());
        verification.setIssueDate(result.issueDate());

        if (kycVerificationRepository.existsByIdNumberHashAndStatusAndUserIdNot(
                idHash, KycStatus.PASSED, userId)) {
            verification.setStatus(KycStatus.FAILED);
            verification.setRejectReason("CCCD đã được xác minh cho tài khoản khác");
            failurePersistenceService.save(verification);
            throw new BadRequestException("KYC thất bại: CCCD đã được xác minh cho tài khoản khác");
        }

        KycVerification saved = kycVerificationRepository.save(verification);
        return mapOcr(saved);
    }

    @Override
    public KycFaceMatchResponse verifyFace(Long userId, Long verificationId, MultipartFile selfie) {
        validateImage(selfie, "Ảnh khuôn mặt");
        KycVerification verification = kycVerificationRepository.findByIdAndUserId(verificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("KycVerification", "id", verificationId));
        if (verification.getStatus() != KycStatus.OCR_PASSED) {
            throw new BadRequestException("Lần xác minh KYC này không còn hợp lệ");
        }
        requireDraft(userId, verification.getRequestedRole());

        String selfieUrl = cloudinaryUploadService.uploadImage(selfie, "hoser/kyc/" + userId);
        FptFaceMatchResult result;
        try {
            byte[] frontBytes = fptAiClient.download(verification.getFrontImageUrl());
            result = fptAiClient.callFaceMatch(frontBytes, "cccd-front.jpg", selfie);
        } catch (IllegalStateException ex) {
            result = new FptFaceMatchResult(false, null, null, ex.getMessage());
        }

        verification.setSelfieImageUrl(selfieUrl);
        verification.setRawFaceResponse(result.rawResponse());
        verification.setFaceScore(result.similarity());
        verification.setFaceMatched(result.matched());
        if (!result.matched()) {
            verification.setStatus(KycStatus.FAILED);
            verification.setRejectReason(result.rejectReason());
            failurePersistenceService.save(verification);
            throw new BadRequestException("KYC thất bại: " + result.rejectReason());
        }

        Long profileId = completionService.complete(verificationId, userId, selfieUrl, result);
        return KycFaceMatchResponse.builder()
                .kycVerificationId(verification.getId())
                .profileId(profileId)
                .requestedRole(verification.getRequestedRole())
                .kycStatus(KycStatus.PASSED)
                .applicationStatus(RoleApprovalStatus.PENDING)
                .faceScore(result.similarity())
                .build();
    }

    private void requireDraft(Long userId, UserRole role) {
        if (role == null) throw new BadRequestException("requestedRole là bắt buộc");
        boolean draft = switch (role) {
            case OWNER -> ownerProfileRepository.findByUserId(userId)
                    .map(p -> p.getStatus() == RoleApprovalStatus.DRAFT).orElse(false);
            case JOCKEY -> jockeyProfileRepository.findByUserId(userId)
                    .map(p -> p.getStatus() == JockeyStatus.DRAFT).orElse(false);
            case REFEREE -> refereeProfileRepository.findByUserId(userId)
                    .map(p -> p.getStatus() == RoleApprovalStatus.DRAFT).orElse(false);
            default -> throw new BadRequestException("KYC chỉ áp dụng cho OWNER, JOCKEY hoặc REFEREE");
        };
        if (!draft) {
            throw new BadRequestException("Vui lòng nhập đầy đủ hồ sơ role ở trạng thái DRAFT trước khi KYC");
        }
    }

    private User requireUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getRole() != UserRole.USER || user.getRoleApprovalStatus() == RoleApprovalStatus.PENDING) {
            throw new BadRequestException("Tài khoản không thể bắt đầu KYC cho role mới");
        }
        return user;
    }

    private void validateImage(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(fieldName + " là bắt buộc");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BadRequestException(fieldName + " vượt quá dung lượng 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(fieldName + " chỉ chấp nhận JPEG hoặc PNG");
        }
    }

    private KycOcrResponse mapOcr(KycVerification verification) {
        return KycOcrResponse.builder()
                .kycVerificationId(verification.getId())
                .requestedRole(verification.getRequestedRole())
                .kycStatus(verification.getStatus())
                .idNumberMasked(verification.getIdNumberMasked())
                .fullName(verification.getFullName())
                .dateOfBirth(verification.getDateOfBirth())
                .gender(verification.getGender())
                .address(verification.getAddress())
                .issueDate(verification.getIssueDate())
                .build();
    }

    private String normalizeIdNumber(String idNumber) {
        if (idNumber == null) throw new BadRequestException("Không đọc được số CCCD");
        return idNumber.replaceAll("\\s+", "");
    }

    private String maskIdNumber(String idNumber) {
        int visible = Math.min(4, idNumber.length());
        return "*".repeat(Math.max(0, idNumber.length() - visible))
                + idNumber.substring(idNumber.length() - visible);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
