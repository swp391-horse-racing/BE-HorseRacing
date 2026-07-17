package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.WalletResponse;
import com.minhthien.hoser_backend.dto.response.WalletTransactionResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.WalletService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/wallets/me")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getCurrentUserWallet(currentUser.getId())));
    }

    @GetMapping("/wallets/me/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getMyWalletTransactions(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getCurrentUserTransactions(currentUser.getId())));
    }

    @GetMapping("/admin/wallet")
    public ResponseEntity<ApiResponse<WalletResponse>> getAdminWallet() {
        return ResponseEntity.ok(ApiResponse.success(walletService.getAdminWalletResponse()));
    }

    @GetMapping("/admin/wallet/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getAdminWalletTransactions() {
        return ResponseEntity.ok(ApiResponse.success(walletService.getAdminWalletTransactions()));
    }
}
