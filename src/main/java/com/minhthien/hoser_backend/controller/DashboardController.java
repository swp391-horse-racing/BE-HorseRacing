package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.DashboardResponse;
import com.minhthien.hoser_backend.dto.response.JockeyPerformanceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.WalletTransactionResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/users/me/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getCurrentUserDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getCurrentUserDashboard(currentUser.getId())));
    }

    @GetMapping("/owner/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getOwnerDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOwnerDashboard(currentUser.getId())));
    }

    @GetMapping("/owner/races")
    public ResponseEntity<ApiResponse<List<RaceResponse>>> getOwnerRaces(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOwnerRaces(currentUser.getId())));
    }

    @GetMapping("/owner/prizes")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getOwnerPrizes(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getOwnerPrizes(currentUser.getId())));
    }

    @GetMapping("/jockey/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getJockeyDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getJockeyDashboard(currentUser.getId())));
    }

    @GetMapping("/jockey/races")
    public ResponseEntity<ApiResponse<List<RaceResponse>>> getJockeyRaces(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getJockeyRaces(currentUser.getId())));
    }

    @GetMapping("/jockey/performance")
    public ResponseEntity<ApiResponse<JockeyPerformanceResponse>> getJockeyPerformance(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getJockeyPerformance(currentUser.getId())));
    }

    @GetMapping("/jockey/prizes")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getJockeyPrizes(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getJockeyPrizes(currentUser.getId())));
    }

    @GetMapping("/referee/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getRefereeDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getRefereeDashboard(currentUser.getId())));
    }

    @GetMapping("/spectator/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getSpectatorDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getSpectatorDashboard(currentUser.getId())));
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getAdminDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAdminDashboard(currentUser.getId())));
    }

    @GetMapping("/admin/races")
    public ResponseEntity<ApiResponse<List<RaceResponse>>> getAdminRaces(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) RaceStatus status) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getAdminRaces(from, to, status)));
    }
}
