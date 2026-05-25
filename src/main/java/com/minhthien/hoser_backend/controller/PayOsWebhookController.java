package com.minhthien.hoser_backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.PaymentOrderResponse;
import com.minhthien.hoser_backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.Webhook;

@RestController
@RequestMapping("/api/payos")
@RequiredArgsConstructor
public class PayOsWebhookController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @GetMapping("/webhook")
    public ResponseEntity<ApiResponse<?>> checkPayOsWebhook() {
        return ResponseEntity.ok(ApiResponse.success("payOS webhook endpoint active", null));
    }

    @PostMapping("/webhook")
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
}
