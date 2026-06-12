package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.*;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.PublicBrandingResponse;
import com.minhthien.hoser_backend.dto.response.SystemSettingsResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.SystemSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class SystemSettingsController {
    private final SystemSettingsService settingsService;

    @GetMapping("/api/v1/system-settings/branding")
    public ResponseEntity<ApiResponse<PublicBrandingResponse>> getPublicBranding() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getPublicBranding()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/v1/admin/system-settings")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(settingsService.getSettings()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/v1/admin/system-settings/fees")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateFees(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody SystemFeesSettingsRequest request) {
        return ok(settingsService.updateFees(admin.getId(), request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/v1/admin/system-settings/rules")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateRules(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody SystemRulesSettingsRequest request) {
        return ok(settingsService.updateRules(admin.getId(), request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/v1/admin/system-settings/email-templates")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateEmailTemplates(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody SystemEmailTemplatesSettingsRequest request) {
        return ok(settingsService.updateEmailTemplates(admin.getId(), request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/v1/admin/system-settings/security")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateSecurity(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody SystemSecuritySettingsRequest request) {
        return ok(settingsService.updateSecurity(admin.getId(), request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/v1/admin/system-settings/branding")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateBranding(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody SystemBrandingSettingsRequest request) {
        return ok(settingsService.updateBranding(admin.getId(), request));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/api/v1/admin/system-settings/race-distances")
    public ResponseEntity<ApiResponse<SystemSettingsResponse>> updateRaceDistances(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody SystemRaceDistancesSettingsRequest request) {
        return ok(settingsService.updateRaceDistances(admin.getId(), request));
    }

    private ResponseEntity<ApiResponse<SystemSettingsResponse>> ok(SystemSettingsResponse response) {
        return ResponseEntity.ok(ApiResponse.success("System settings updated", response));
    }
}
