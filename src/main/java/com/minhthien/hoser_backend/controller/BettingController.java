package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.BetMarketRequest;
import com.minhthien.hoser_backend.dto.request.BetRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.BetMarketResponse;
import com.minhthien.hoser_backend.dto.response.BetResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.BettingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn"
})
public class BettingController {
    private final BettingService bettingService;

    @PostMapping("/admin/races/{raceId}/bet-market")
    public ResponseEntity<ApiResponse<BetMarketResponse>> createBetMarket(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long raceId,
            @Valid @RequestBody BetMarketRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bet market created",
                bettingService.createBetMarket(currentUser.getId(), raceId, request)));
    }

    @PutMapping("/admin/bet-markets/{id}/open")
    public ResponseEntity<ApiResponse<BetMarketResponse>> openBetMarket(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Bet market opened",
                bettingService.openBetMarket(currentUser.getId(), id)));
    }

    @PutMapping("/admin/bet-markets/{id}/close")
    public ResponseEntity<ApiResponse<BetMarketResponse>> closeBetMarket(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Bet market closed",
                bettingService.closeBetMarket(currentUser.getId(), id)));
    }

    @GetMapping("/admin/bet-markets")
    public ResponseEntity<ApiResponse<List<BetMarketResponse>>> getAdminBetMarkets(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                bettingService.getAdminBetMarkets(currentUser.getId())));
    }

    @GetMapping("/admin/bet-markets/{id}/bets")
    public ResponseEntity<ApiResponse<List<BetResponse>>> getAdminMarketBets(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                bettingService.getAdminMarketBets(currentUser.getId(), id)));
    }

    @GetMapping("/races/{raceId}/bet-market")
    public ResponseEntity<ApiResponse<BetMarketResponse>> getPublicOpenBetMarket(@PathVariable Long raceId) {
        return ResponseEntity.ok(ApiResponse.success(bettingService.getPublicOpenBetMarket(raceId)));
    }

    @GetMapping("/users/me/bettable-races")
    public ResponseEntity<ApiResponse<List<BetMarketResponse>>> getMyBettableRaceMarkets(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(bettingService.getBettableRaceMarkets(currentUser.getId())));
    }

    @PostMapping("/races/{raceId}/bets")
    public ResponseEntity<ApiResponse<BetResponse>> placeBet(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long raceId,
            @Valid @RequestBody BetRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bet placed",
                bettingService.placeBet(currentUser.getId(), raceId, request)));
    }

    @GetMapping("/users/me/bets")
    public ResponseEntity<ApiResponse<List<BetResponse>>> getMyBets(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(bettingService.getUserBets(currentUser.getId())));
    }

    @GetMapping("/bets/{id}")
    public ResponseEntity<ApiResponse<BetResponse>> getMyBet(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bettingService.getUserBet(currentUser.getId(), id)));
    }
}
