package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.request.TournamentRequest;
import com.minhthien.hoser_backend.dto.request.TournamentUpdateRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.TournamentFinalizationResponse;
import com.minhthien.hoser_backend.dto.response.TournamentLeaderboardResponse;
import com.minhthien.hoser_backend.dto.response.TournamentPayoutResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.dto.response.TournamentStatisticsResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.service.TournamentFinalizationService;
import com.minhthien.hoser_backend.service.TournamentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class TournamentController {
    private final TournamentService tournamentService;
    private final TournamentFinalizationService tournamentFinalizationService;
    private final MultipartJsonParser multipartJsonParser;

    @PostMapping(value = "/admin/tournaments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<TournamentResponse>> createTournament(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody TournamentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tournament created",
                tournamentService.createTournament(currentUser.getId(), request)));
    }

    @PostMapping(value = "/admin/tournaments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TournamentResponse>> createTournamentWithBanner(
            @AuthenticationPrincipal User currentUser,
            @RequestPart("data") String data,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {
        TournamentRequest request = multipartJsonParser.parse(data, TournamentRequest.class);
        return ResponseEntity.ok(ApiResponse.success("Tournament created",
                tournamentService.createTournament(currentUser.getId(), request, banner)));
    }

    @PutMapping(value = "/admin/tournaments/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<TournamentResponse>> updateTournament(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody TournamentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tournament updated",
                tournamentService.updateTournament(currentUser.getId(), id, request)));
    }

    @PutMapping(value = "/admin/tournaments/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TournamentResponse>> updateTournamentWithBanner(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestPart("data") String data,
            @RequestPart(value = "banner", required = false) MultipartFile banner) {
        TournamentUpdateRequest request = multipartJsonParser.parse(data, TournamentUpdateRequest.class);
        return ResponseEntity.ok(ApiResponse.success("Tournament updated",
                tournamentService.updateTournament(currentUser.getId(), id, request, banner)));
    }

    @PostMapping("/admin/tournaments/{id}/races")
    public ResponseEntity<ApiResponse<TournamentResponse>> addTournamentRace(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RaceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tournament race created",
                tournamentService.addTournamentRace(currentUser.getId(), id, request)));
    }

    @PutMapping("/admin/tournaments/{id}/races")
    public ResponseEntity<ApiResponse<TournamentResponse>> replaceTournamentRaces(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody List<@Valid RaceRequest> requests) {
        return ResponseEntity.ok(ApiResponse.success("Tournament races updated",
                tournamentService.replaceTournamentRaces(currentUser.getId(), id, requests)));
    }

    @PutMapping("/admin/tournaments/{id}/status")
    public ResponseEntity<ApiResponse<TournamentResponse>> updateTournamentStatus(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestParam TournamentStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Tournament status updated",
                tournamentService.updateTournamentStatus(currentUser.getId(), id, status)));
    }

    @PutMapping("/admin/tournaments/{id}/open-registration")
    public ResponseEntity<ApiResponse<TournamentResponse>> openRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tournament registration opened",
                tournamentService.openRegistration(currentUser.getId(), id)));
    }

    @PutMapping("/admin/tournaments/{id}/close-registration")
    public ResponseEntity<ApiResponse<TournamentResponse>> closeRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tournament registration closed",
                tournamentService.closeRegistration(currentUser.getId(), id)));
    }

    @PutMapping("/admin/tournaments/{id}/finalize")
    public ResponseEntity<ApiResponse<TournamentFinalizationResponse>> finalizeTournament(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tournament finalized",
                tournamentFinalizationService.finalizeTournament(currentUser.getId(), id)));
    }

    @GetMapping("/admin/tournaments")
    public ResponseEntity<ApiResponse<List<TournamentResponse>>> getAdminTournaments(
            @RequestParam(required = false) TournamentStatus status) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getAdminTournaments(status)));
    }

    @GetMapping("/admin/tournaments/{id}")
    public ResponseEntity<ApiResponse<TournamentResponse>> getAdminTournament(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getAdminTournament(id)));
    }

    @GetMapping("/admin/tournaments/{id}/statistics")
    public ResponseEntity<ApiResponse<TournamentStatisticsResponse>> getTournamentStatistics(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                tournamentFinalizationService.getStatistics(currentUser.getId(), id)));
    }

    @GetMapping("/admin/tournaments/{id}/payouts")
    public ResponseEntity<ApiResponse<List<TournamentPayoutResponse>>> getTournamentPayouts(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                tournamentFinalizationService.getPayouts(currentUser.getId(), id)));
    }

    @GetMapping("/tournaments")
    public ResponseEntity<ApiResponse<List<TournamentResponse>>> getPublicTournaments() {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getPublicTournaments()));
    }

    @GetMapping("/tournaments/{id}")
    public ResponseEntity<ApiResponse<TournamentResponse>> getPublicTournament(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getPublicTournament(id)));
    }

    @GetMapping("/tournaments/{id}/races")
    public ResponseEntity<ApiResponse<List<RaceResponse>>> getPublicTournamentRaces(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentService.getPublicTournamentRaces(id)));
    }

    @GetMapping("/tournaments/{id}/leaderboard")
    public ResponseEntity<ApiResponse<TournamentLeaderboardResponse>> getPublicTournamentLeaderboard(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tournamentFinalizationService.getLeaderboard(id)));
    }
}
