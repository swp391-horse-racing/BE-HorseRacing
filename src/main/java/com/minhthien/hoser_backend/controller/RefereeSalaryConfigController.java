package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.RefereeSalaryConfigRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.RefereeSalaryConfigResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.RefereeSalaryConfigService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/referee-salary-configs")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RefereeSalaryConfigController {
    private final RefereeSalaryConfigService configService;

    @PostMapping
    public ResponseEntity<ApiResponse<RefereeSalaryConfigResponse>> create(
            @AuthenticationPrincipal User admin,
            @Valid @RequestBody RefereeSalaryConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Referee salary config created", configService.create(admin.getId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RefereeSalaryConfigResponse>>> getAll(
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(ApiResponse.success(configService.getAll(admin.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RefereeSalaryConfigResponse>> getById(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(configService.getById(admin.getId(), id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RefereeSalaryConfigResponse>> update(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id,
            @Valid @RequestBody RefereeSalaryConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Referee salary config updated", configService.update(admin.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal User admin,
            @PathVariable Long id) {
        configService.delete(admin.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Referee salary config deleted", null));
    }
}
