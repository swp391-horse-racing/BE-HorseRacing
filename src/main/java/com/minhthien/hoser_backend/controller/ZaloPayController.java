package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/zalopay")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn",
        "https://api.horseracing.id.vn"
})
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
