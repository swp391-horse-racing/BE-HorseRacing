package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.CreateDepositOrderRequest;
import com.minhthien.hoser_backend.dto.request.DepositCallbackRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.PaymentCallbackLogResponse;
import com.minhthien.hoser_backend.dto.response.PaymentOrderResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn"
})
public class PaymentController {

    private final PaymentService paymentService;

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/wallets/me/deposit-orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createDepositOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateDepositOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Deposit order created",
                paymentService.createDepositOrder(currentUser.getId(), request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/wallets/me/deposit-orders")
    public ResponseEntity<ApiResponse<List<PaymentOrderResponse>>> getMyDepositOrders(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getUserDepositOrders(currentUser.getId())));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/wallets/me/deposit-orders/{id}")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> getMyDepositOrder(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getUserDepositOrder(currentUser.getId(), id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/admin/wallet/deposit-orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createAdminWalletDepositOrder(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateDepositOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Admin wallet deposit order created",
                paymentService.createAdminWalletDepositOrder(currentUser.getId(), request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/wallet/deposit-orders")
    public ResponseEntity<ApiResponse<List<PaymentOrderResponse>>> getAdminWalletDepositOrders() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAdminWalletDepositOrders()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/wallet/deposit-orders/{id}")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> getAdminWalletDepositOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAdminWalletDepositOrder(id)));
    }

    @PostMapping("/payment-callbacks/deposits")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> handleDepositCallback(
            @Valid @RequestBody DepositCallbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Deposit callback processed",
                paymentService.handleDepositCallback(request)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/payment-orders")
    public ResponseEntity<ApiResponse<List<PaymentOrderResponse>>> getAdminPaymentOrders() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAdminPaymentOrders()));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/payment-orders/{id}")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> getAdminPaymentOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAdminPaymentOrder(id)));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin/payment-callback-logs")
    public ResponseEntity<ApiResponse<List<PaymentCallbackLogResponse>>> getAdminPaymentCallbackLogs() {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getAdminPaymentCallbackLogs()));
    }
}
