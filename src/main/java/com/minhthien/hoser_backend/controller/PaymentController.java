package com.minhthien.hoser_backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

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

    @PostMapping("/payment-callbacks/deposits")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> handleDepositCallback(
            @Valid @RequestBody DepositCallbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Deposit callback processed",
                paymentService.handleDepositCallback(request)));
    }

    @GetMapping("/wallets/top-up/payos/webhook")
    public ResponseEntity<ApiResponse<?>> checkPayOsWebhook() {
        return ResponseEntity.ok(ApiResponse.success("payOS webhook endpoint active", null));
    }

    @PostMapping("/wallets/top-up/payos/webhook")
    public ResponseEntity<ApiResponse<?>> handlePayOsWebhook(@RequestBody(required = false) String body) {
        Webhook webhook = parsePayOsWebhook(body);
        if (isPayOsWebhookProbe(webhook)) {
            return ResponseEntity.ok(ApiResponse.success("payOS webhook endpoint active", null));
        }
        return ResponseEntity.ok(ApiResponse.success("payOS webhook processed",
                paymentService.handlePayOsWebhook(webhook)));
    }

    private Webhook parsePayOsWebhook(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Webhook.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private boolean isPayOsWebhookProbe(Webhook webhook) {
        if (webhook == null || webhook.getData() == null) {
            return true;
        }
        return webhook.getData().getOrderCode() == null
                && (webhook.getData().getPaymentLinkId() == null || webhook.getData().getPaymentLinkId().isBlank());
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
