package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.RaceTrackRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.RaceTrackResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.RaceTrackService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/race-tracks")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class RaceTrackController {
    private final RaceTrackService raceTrackService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RaceTrackResponse>>> getRaceTracks(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String locationKey,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                raceTrackService.getRaceTracks(currentUser.getId(), locationKey, active)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RaceTrackResponse>> createRaceTrack(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody RaceTrackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race track created",
                raceTrackService.createRaceTrack(currentUser.getId(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RaceTrackResponse>> updateRaceTrack(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RaceTrackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race track updated",
                raceTrackService.updateRaceTrack(currentUser.getId(), id, request)));
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<ApiResponse<RaceTrackResponse>> updateRaceTrackActive(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestParam Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Race track active status updated",
                raceTrackService.updateRaceTrackActive(currentUser.getId(), id, active)));
    }
}
