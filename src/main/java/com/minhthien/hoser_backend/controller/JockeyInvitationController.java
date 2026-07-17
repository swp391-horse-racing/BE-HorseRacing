package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.JockeyInvitationResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.JockeyInvitationService;
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
public class JockeyInvitationController {
    private final JockeyInvitationService jockeyInvitationService;

    @PostMapping("/owner/jockey-invitations")
    public ResponseEntity<ApiResponse<JockeyInvitationResponse>> createInvitation(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody JockeyInvitationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Jockey invitation created",
                jockeyInvitationService.createInvitation(currentUser.getId(), request)));
    }

    @GetMapping("/owner/jockey-invitations")
    public ResponseEntity<ApiResponse<List<JockeyInvitationResponse>>> getOwnerInvitations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(jockeyInvitationService.getOwnerInvitations(currentUser.getId())));
    }

    @GetMapping("/owner/jockey-invitations/{id}")
    public ResponseEntity<ApiResponse<JockeyInvitationResponse>> getOwnerInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                jockeyInvitationService.getOwnerInvitation(currentUser.getId(), id)));
    }

    @GetMapping("/owners/me/jockeys")
    public ResponseEntity<ApiResponse<List<JockeyInvitationResponse>>> getOwnerAcceptedJockeys(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                jockeyInvitationService.getOwnerAcceptedJockeys(currentUser.getId())));
    }

    @PutMapping("/owner/jockey-invitations/{id}/cancel")
    public ResponseEntity<ApiResponse<JockeyInvitationResponse>> cancelInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Jockey invitation cancelled",
                jockeyInvitationService.cancelInvitation(currentUser.getId(), id)));
    }

    @GetMapping("/jockey/invitations")
    public ResponseEntity<ApiResponse<List<JockeyInvitationResponse>>> getJockeyInvitations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(jockeyInvitationService.getJockeyInvitations(currentUser.getId())));
    }

    @GetMapping("/jockey/invitations/{id}")
    public ResponseEntity<ApiResponse<JockeyInvitationResponse>> getJockeyInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                jockeyInvitationService.getJockeyInvitation(currentUser.getId(), id)));
    }

    @PutMapping("/jockey/invitations/{id}/accept")
    public ResponseEntity<ApiResponse<JockeyInvitationResponse>> acceptInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) @Valid InvitationDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Jockey invitation accepted",
                jockeyInvitationService.acceptInvitation(currentUser.getId(), id, request)));
    }

    @PutMapping("/jockey/invitations/{id}/reject")
    public ResponseEntity<ApiResponse<JockeyInvitationResponse>> rejectInvitation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @RequestBody(required = false) @Valid InvitationDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Jockey invitation rejected",
                jockeyInvitationService.rejectInvitation(currentUser.getId(), id, request)));
    }

}
