package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.RefereeInvitationRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.RefereeInvitationResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.RefereeInvitationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class RefereeInvitationController {
    private final RefereeInvitationService refereeInvitationService;

    @PostMapping("/admin/referee-invitations")
    public ResponseEntity<ApiResponse<RefereeInvitationResponse>> createInvitation(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody RefereeInvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Referee invitation created",
                refereeInvitationService.createInvitation(currentUser.getId(), request)));
    }

    @GetMapping("/admin/referee-invitations")
    public ResponseEntity<ApiResponse<List<RefereeInvitationResponse>>> getAdminInvitations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                refereeInvitationService.getAdminInvitations(currentUser.getId())));
    }

    @GetMapping("/admin/referee-invitations/{id}")
    public ResponseEntity<ApiResponse<RefereeInvitationResponse>> getAdminInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                refereeInvitationService.getAdminInvitation(currentUser.getId(), id)));
    }

    @PutMapping("/admin/referee-invitations/{id}/cancel")
    public ResponseEntity<ApiResponse<RefereeInvitationResponse>> cancelInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Referee invitation cancelled",
                refereeInvitationService.cancelInvitation(currentUser.getId(), id)));
    }

    @GetMapping("/referee/invitations")
    public ResponseEntity<ApiResponse<List<RefereeInvitationResponse>>> getRefereeInvitations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                refereeInvitationService.getRefereeInvitations(currentUser.getId())));
    }

    @GetMapping("/referee/invitations/{id}")
    public ResponseEntity<ApiResponse<RefereeInvitationResponse>> getRefereeInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                refereeInvitationService.getRefereeInvitation(currentUser.getId(), id)));
    }

    @PutMapping("/referee/invitations/{id}/accept")
    public ResponseEntity<ApiResponse<RefereeInvitationResponse>> acceptInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) InvitationDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Referee invitation accepted",
                refereeInvitationService.acceptInvitation(currentUser.getId(), id, request)));
    }

    @PutMapping("/referee/invitations/{id}/reject")
    public ResponseEntity<ApiResponse<RefereeInvitationResponse>> rejectInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) InvitationDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Referee invitation rejected",
                refereeInvitationService.rejectInvitation(currentUser.getId(), id, request)));
    }
}
