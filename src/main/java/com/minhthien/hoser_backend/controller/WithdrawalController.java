package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.AdminWalletWithdrawalRequest;
import com.minhthien.hoser_backend.dto.request.CreateWithdrawalRequest;
import com.minhthien.hoser_backend.dto.request.WithdrawalDecisionRequest;
import com.minhthien.hoser_backend.dto.response.AdminWalletWithdrawalResponse;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.WithdrawalResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.WithdrawalStatus;
import com.minhthien.hoser_backend.service.WithdrawalService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping("/wallets/me/withdrawals")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> createMyWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateWithdrawalRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal requested",
                withdrawalService.createUserWithdrawal(currentUser.getId(), request)));
    }

    @GetMapping("/wallets/me/withdrawals")
    public ResponseEntity<ApiResponse<List<WithdrawalResponse>>> getMyWithdrawals(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getUserWithdrawals(currentUser.getId())));
    }

    @GetMapping("/wallets/me/withdrawals/{id}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> getMyWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getUserWithdrawal(currentUser.getId(), id)));
    }

    @GetMapping("/admin/withdrawals")
    public ResponseEntity<ApiResponse<List<WithdrawalResponse>>> getAdminWithdrawals(
            @RequestParam(required = false) WithdrawalStatus status) {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getAdminWithdrawals(status)));
    }

    @GetMapping("/admin/withdrawals/{id}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> getAdminWithdrawal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getAdminWithdrawal(id)));
    }

    @PutMapping("/admin/withdrawals/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approveWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) WithdrawalDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal approved",
                withdrawalService.approveWithdrawal(id, currentUser.getId(), request)));
    }

    @PutMapping("/admin/withdrawals/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> rejectWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) WithdrawalDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal rejected",
                withdrawalService.rejectWithdrawal(id, currentUser.getId(), request)));
    }

    @PutMapping("/admin/withdrawals/{id}/mark-paid")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> markWithdrawalPaid(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) WithdrawalDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Withdrawal marked paid",
                withdrawalService.markWithdrawalPaid(id, currentUser.getId(), request)));
    }

    @PostMapping("/admin/wallet/withdrawals")
    public ResponseEntity<ApiResponse<AdminWalletWithdrawalResponse>> createAdminWalletWithdrawal(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody AdminWalletWithdrawalRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Admin wallet withdrawal paid",
                withdrawalService.createAdminWithdrawal(currentUser.getId(), request)));
    }

    @GetMapping("/admin/wallet/withdrawals")
    public ResponseEntity<ApiResponse<List<AdminWalletWithdrawalResponse>>> getAdminWalletWithdrawals() {
        return ResponseEntity.ok(ApiResponse.success(withdrawalService.getAdminWalletWithdrawals()));
    }
}
