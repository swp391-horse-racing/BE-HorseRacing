package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.RoleApplicationResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.service.RoleApplicationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/role-applications")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn",
        "https://api.horseracing.id.vn"
})
public class AdminRoleApplicationController {
    private final RoleApplicationService roleApplicationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleApplicationResponse>>> getRoleApplications(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) RoleApprovalStatus status) {
        return ResponseEntity.ok(ApiResponse.success(roleApplicationService.getAdminApplications(role, status)));
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<ApiResponse<List<RoleApplicationResponse>>> getRoleApplicationsByRole(
            @PathVariable UserRole role) {
        return ResponseEntity.ok(ApiResponse.success(roleApplicationService.getAdminApplications(role, null)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<RoleApplicationResponse>>> getRoleApplicationsByStatus(
            @PathVariable RoleApprovalStatus status) {
        return ResponseEntity.ok(ApiResponse.success(roleApplicationService.getAdminApplications(null, status)));
    }

    @PutMapping("/{profileId}/approve")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> approveRoleApplication(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long profileId,
            @RequestParam(required = false) UserRole role) {
        return ResponseEntity.ok(ApiResponse.success("Role application approved",
                roleApplicationService.approveApplication(profileId, currentUser.getId(), role)));
    }

    @PutMapping("/{profileId}/reject")
    public ResponseEntity<ApiResponse<RoleApplicationResponse>> rejectRoleApplication(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long profileId,
            @RequestParam(required = false) UserRole role,
            @Valid @RequestBody AdminReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role application rejected",
                roleApplicationService.rejectApplication(profileId, currentUser.getId(), role, request)));
    }
}
