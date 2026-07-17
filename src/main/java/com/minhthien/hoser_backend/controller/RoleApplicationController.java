package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.OwnerRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.RefereeRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.SpectatorRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.KycFaceMatchResponse;
import com.minhthien.hoser_backend.dto.response.KycOcrResponse;
import com.minhthien.hoser_backend.dto.response.RoleApplicationResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.service.KycService;
import com.minhthien.hoser_backend.service.RoleApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/role-applications")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RoleApplicationController {
    private final RoleApplicationService roleApplicationService;
    private final KycService kycService;

    @PostMapping(value = "/owner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitOwnerApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute OwnerRoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Owner role draft saved",
                roleApplicationService.submitOwnerApplication(
                        currentUser.getId(), request, request.getVerificationDocument())));
    }

    @PostMapping(value = "/jockey", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitJockeyApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute JockeyProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Jockey role draft saved",
                roleApplicationService.submitJockeyApplication(
                        currentUser.getId(), request, request.getAvatar(), request.getLicenseDocument())));
    }

    @PostMapping(value = "/spectator", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitSpectatorApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody SpectatorRoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Spectator role draft saved",
                roleApplicationService.submitSpectatorApplication(currentUser.getId(), request)));
    }

    @PostMapping(value = "/referee", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitRefereeApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute RefereeRoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Referee role draft saved",
                roleApplicationService.submitRefereeApplication(
                        currentUser.getId(), request, request.getCertificationDocument())));
    }

    @PostMapping(value = "/kyc/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KycOcrResponse>> verifyCccd(
            @AuthenticationPrincipal User currentUser,
            @RequestParam UserRole requestedRole,
            @RequestPart("cccdFront") MultipartFile cccdFront,
            @RequestPart("cccdBack") MultipartFile cccdBack) {
        return ResponseEntity.ok(ApiResponse.success("OCR CCCD thành công",
                kycService.verifyCccd(currentUser.getId(), requestedRole, cccdFront, cccdBack)));
    }

    @PostMapping(value = "/kyc/{kycVerificationId}/face-match",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KycFaceMatchResponse>> verifyFace(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long kycVerificationId,
            @RequestPart("selfie") MultipartFile selfie) {
        return ResponseEntity.ok(ApiResponse.success(
                "KYC thành công, hồ sơ đã được gửi cho admin duyệt",
                kycService.verifyFace(currentUser.getId(), kycVerificationId, selfie)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> getMyApplication(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(roleApplicationService.getMyApplication(currentUser.getId())));
    }
}
