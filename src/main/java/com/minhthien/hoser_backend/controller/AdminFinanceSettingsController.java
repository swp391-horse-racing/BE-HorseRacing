package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.FinanceSettingsRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeShareSettingsRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.FinanceSettingsResponse;
import com.minhthien.hoser_backend.dto.response.RacePrizeShareSettingsResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/finance-settings")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn",
        "https://api.horseracing.id.vn"
})
public class AdminFinanceSettingsController {
    private final FinanceSettingsService financeSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<FinanceSettingsResponse>> getFinanceSettings() {
        return ResponseEntity.ok(ApiResponse.success(financeSettingsService.getFinanceSettings()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<FinanceSettingsResponse>> updateFinanceSettings(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody FinanceSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Finance settings updated",
                financeSettingsService.updateFinanceSettings(request, currentUser.getUsername())));
    }

    @GetMapping("/race-prize-shares")
    public ResponseEntity<ApiResponse<RacePrizeShareSettingsResponse>> getRacePrizeShareSettings() {
        return ResponseEntity.ok(ApiResponse.success(financeSettingsService.getRacePrizeShareSettings()));
    }

    @PutMapping("/race-prize-shares")
    public ResponseEntity<ApiResponse<RacePrizeShareSettingsResponse>> updateRacePrizeShareSettings(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody RacePrizeShareSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race prize share settings updated",
                financeSettingsService.updateRacePrizeShareSettings(request, currentUser.getUsername())));
    }
}
