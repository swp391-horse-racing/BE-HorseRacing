package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/zalopay")
@RequiredArgsConstructor
public class ZaloPayController {

    private final PaymentService paymentService;

    @GetMapping("/return")
    public ResponseEntity<Map<String, Object>> handleReturn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleZaloPayReturn(params));
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(paymentService.handleZaloPayCallback(payload));
    }
}
