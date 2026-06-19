package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.HorseRequest;
import com.minhthien.hoser_backend.dto.request.HorseUpdateRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.HorseResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.service.HorseService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
public class HorseController {
    private final HorseService horseService;

    @GetMapping("/horses/approved")
    public ResponseEntity<ApiResponse<List<HorseResponse>>> getApprovedHorses() {
        return ResponseEntity.ok(ApiResponse.success(horseService.getApprovedHorses()));
    }

    @PostMapping(value = "/owner/horses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HorseResponse>> createHorse(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute HorseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Horse created",
                horseService.createHorse(currentUser.getId(), request, request.getImage(), request.getDocument())));
    }

    @GetMapping("/owner/horses")
    public ResponseEntity<ApiResponse<List<HorseResponse>>> getOwnerHorses(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(horseService.getOwnerHorses(currentUser.getId())));
    }

    @GetMapping("/owner/horses/{id}")
    public ResponseEntity<ApiResponse<HorseResponse>> getOwnerHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(horseService.getOwnerHorse(currentUser.getId(), id)));
    }

    @GetMapping("/horses/{id}")
    public ResponseEntity<ApiResponse<HorseResponse>> getHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        Long viewerId = currentUser == null ? null : currentUser.getId();
        return ResponseEntity.ok(ApiResponse.success(horseService.getHorseForViewer(viewerId, id)));
    }

    @PutMapping(value = "/owner/horses/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HorseResponse>> updateHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @ModelAttribute HorseUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Horse updated",
                horseService.updateHorse(currentUser.getId(), id, request, request.getImage(), request.getDocument())));
    }

    @DeleteMapping("/owner/horses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        horseService.deleteHorse(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Horse deleted", null));
    }

    @GetMapping("/admin/horses")
    public ResponseEntity<ApiResponse<List<HorseResponse>>> getAdminHorses(
            @RequestParam(required = false, defaultValue = "PENDING") HorseStatus status) {
        return ResponseEntity.ok(ApiResponse.success(horseService.getAdminHorses(status)));
    }

    @PutMapping("/admin/horses/{id}/approve")
    public ResponseEntity<ApiResponse<HorseResponse>> approveHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Horse approved",
                horseService.approveHorse(id, currentUser.getId())));
    }

    @PutMapping("/admin/horses/{id}/reject")
    public ResponseEntity<ApiResponse<HorseResponse>> rejectHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Horse rejected",
                horseService.rejectHorse(id, currentUser.getId(), request)));
    }

    @PutMapping("/admin/horses/{id}/suspend")
    public ResponseEntity<ApiResponse<HorseResponse>> suspendHorse(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Horse suspended",
                horseService.suspendHorse(id, currentUser.getId(), request)));
    }
}
