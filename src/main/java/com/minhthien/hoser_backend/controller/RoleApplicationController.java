package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.OwnerRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.RefereeRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.SpectatorRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.RoleApplicationResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.RoleApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/role-applications")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class RoleApplicationController {
    private final RoleApplicationService roleApplicationService;

    @PostMapping(value = "/owner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitOwnerApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute OwnerRoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Owner role application submitted",
                roleApplicationService.submitOwnerApplication(
                        currentUser.getId(), request, request.getVerificationDocument())));
    }

    @PostMapping(value = "/jockey", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitJockeyApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute JockeyProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Jockey role application submitted",
                roleApplicationService.submitJockeyApplication(
                        currentUser.getId(), request, request.getAvatar(), request.getLicenseDocument())));
    }

    @PostMapping(value = "/spectator", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitSpectatorApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody SpectatorRoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Spectator role application submitted",
                roleApplicationService.submitSpectatorApplication(currentUser.getId(), request)));
    }

    @PostMapping(value = "/referee", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> submitRefereeApplication(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute RefereeRoleApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Referee role application submitted",
                roleApplicationService.submitRefereeApplication(
                        currentUser.getId(), request, request.getCertificationDocument())));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> getMyApplication(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(roleApplicationService.getMyApplication(currentUser.getId())));
    }
}
