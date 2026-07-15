package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.kyc.VnptFaceMatchResult;
import com.minhthien.hoser_backend.dto.kyc.VnptOcrResult;
import com.minhthien.hoser_backend.dto.response.KycFaceMatchResponse;
import com.minhthien.hoser_backend.dto.response.KycOcrResponse;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.VnptEkycException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.KycService;
import com.minhthien.hoser_backend.service.kyc.VnptEkycClient;
import com.minhthien.hoser_backend.service.kyc.KycFailurePersistenceService;
import com.minhthien.hoser_backend.service.kyc.KycCompletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KycServiceImpl implements KycService {
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final List<DateTimeFormatter> DOB_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final RefereeProfileRepository refereeProfileRepository;
    private final SpectatorProfileRepository spectatorProfileRepository;
    private final KycVerificationRepository kycVerificationRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final VnptEkycClient vnptEkycClient;
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
        VnptOcrResult result;
        try {
            result = vnptEkycClient.callOcr(cccdFront, cccdBack);
        } catch (VnptEkycException ex) {
            failurePersistenceService.save(KycVerification.builder()
                    .user(user)
                    .requestedRole(requestedRole)
                    .provider("VNPT_EKYC")
                    .status(KycStatus.FAILED)
                    .frontImageUrl(frontUrl)
                    .backImageUrl(backUrl)
                    .rejectReason(ex.getMessage())
                    .build());
            throw ex;
        }
        KycVerification verification = KycVerification.builder()
                .user(user)
                .requestedRole(requestedRole)
                .provider("VNPT_EKYC")
                .status(result.passed() ? KycStatus.OCR_PASSED : KycStatus.FAILED)
                .frontOcrPassed(result.passed())
                .frontImageUrl(frontUrl)
                .backImageUrl(backUrl)
                .vnptFrontImageHash(result.frontImageHash())
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
        validateFullNameMatchesCccd(user, result, verification);
        requireAdultForSpectator(verification);

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

        if (verification.getVnptFrontImageHash() == null
                || verification.getVnptFrontImageHash().isBlank()) {
            throw new BadRequestException(
                    "Lần xác minh KYC này chưa có dữ liệu VNPT eKYC. Vui lòng xác minh lại CCCD.");
        }
        String selfieUrl = cloudinaryUploadService.uploadImage(selfie, "hoser/kyc/" + userId);

        VnptFaceMatchResult result;
        try {
            result = vnptEkycClient.callFaceCompare(verification.getVnptFrontImageHash(), selfie);
        } catch (VnptEkycException ex) {
            verification.setSelfieImageUrl(selfieUrl);
            verification.setStatus(KycStatus.FAILED);
            verification.setRejectReason(ex.getMessage());
            failurePersistenceService.save(verification);
            throw ex;
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
            case SPECTATOR -> spectatorProfileRepository.findByUserId(userId)
                    .map(p -> p.getStatus() == RoleApprovalStatus.DRAFT).orElse(false);
            default -> throw new BadRequestException("KYC chỉ áp dụng cho OWNER, JOCKEY, SPECTATOR hoặc REFEREE");
        };
        if (!draft) {
            throw new BadRequestException("Vui lòng nhập đầy đủ hồ sơ role ở trạng thái DRAFT trước khi KYC");
        }
    }

    private void requireAdultForSpectator(KycVerification verification) {
        if (verification.getRequestedRole() != UserRole.SPECTATOR) {
            return;
        }
        LocalDate birthDate = parseBirthDate(verification.getDateOfBirth());
        String reason;
        if (birthDate == null) {
            reason = "Không xác định được tuổi từ CCCD";
        } else if (birthDate.plusYears(18).isAfter(LocalDate.now())) {
            reason = "Spectator phải đủ 18 tuổi mới được tiếp tục KYC";
        } else {
            return;
        }
        verification.setStatus(KycStatus.FAILED);
        verification.setRejectReason(reason);
        failurePersistenceService.save(verification);
        throw new BadRequestException("KYC thất bại: " + reason);
    }

    private LocalDate parseBirthDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : DOB_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
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

    private void validateFullNameMatchesCccd(User user, VnptOcrResult result, KycVerification verification) {
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            verification.setStatus(KycStatus.FAILED);
            verification.setRejectReason("Vui lòng cập nhật họ và tên tài khoản trước khi KYC");
            failurePersistenceService.save(verification);
            throw new BadRequestException("KYC thất bại: Vui lòng cập nhật họ và tên tài khoản trước khi KYC");
        }

        String accountFullName = normalizeFullName(user.getFullName());
        String cccdFullName = normalizeFullName(result.fullName());
        if (cccdFullName.isBlank() || !accountFullName.equals(cccdFullName)) {
            verification.setStatus(KycStatus.FAILED);
            verification.setRejectReason("Họ và tên trên CCCD không khớp với họ tên tài khoản");
            failurePersistenceService.save(verification);
            throw new BadRequestException("KYC thất bại: Họ và tên trên CCCD không khớp với họ tên tài khoản");
        }
    }

    private String normalizeFullName(String fullName) {
        if (fullName == null) {
            return "";
        }
        String collapsed = fullName.trim().replaceAll("\\s+", " ");
        String normalized = Normalizer.normalize(collapsed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('Đ', 'D')
                .replace('đ', 'd');
        return normalized.toUpperCase(Locale.ROOT);
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
