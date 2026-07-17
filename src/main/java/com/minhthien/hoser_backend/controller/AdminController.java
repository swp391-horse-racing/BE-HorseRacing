package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.UserRoleRequest;
import com.minhthien.hoser_backend.dto.response.AdminPayoutDebtSummaryResponse;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.UserResponse;
import com.minhthien.hoser_backend.service.AdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllUsers()));
    }

    @GetMapping("/admin/users/active")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getActiveUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getActiveUsers()));
    }

    @GetMapping("/admin/users/deactivated")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getDeactivatedUsers() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDeactivatedUsers()));
    }

    @GetMapping("/admin/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUserById(id)));
    }

    @PutMapping("/admin/users/{userId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long userId) {
        adminService.deactivateAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Account deactivated", null));
    }

    @PutMapping("/admin/users/{userId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateAccount(@PathVariable Long userId) {
        adminService.activateAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Account activated", null));
    }

    @PutMapping("/admin/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UserRoleRequest request) {
        UserResponse response = adminService.updateUserRole(userId, request.getRole());
        return ResponseEntity.ok(ApiResponse.success("User role updated", response));
    }

    @GetMapping("/admin/payout-debts")
    public ResponseEntity<ApiResponse<AdminPayoutDebtSummaryResponse>> getPayoutDebts() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getPayoutDebts()));
    }
}
