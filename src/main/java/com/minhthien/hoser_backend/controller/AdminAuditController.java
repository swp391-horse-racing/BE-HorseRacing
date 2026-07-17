package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.response.AdminAuditLogResponse;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.service.AdminAuditLogService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn",
        "https://api.horseracing.id.vn"
})
public class AdminAuditController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AdminAuditLogResponse>>> getAdminAuditLogs(
            @RequestParam(required = false) String referenceType,
            @RequestParam(required = false) String referenceId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuditLogService.getAdminAuditLogs(referenceType, referenceId)));
    }
}
