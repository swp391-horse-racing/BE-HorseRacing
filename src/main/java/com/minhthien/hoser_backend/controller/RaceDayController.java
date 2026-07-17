package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.RaceFinalizeResultRequest;
import com.minhthien.hoser_backend.dto.request.RaceCancellationRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintRequest;
import com.minhthien.hoser_backend.dto.request.RaceComplaintResolveRequest;
import com.minhthien.hoser_backend.dto.request.RaceGateUpdateRequest;
import com.minhthien.hoser_backend.dto.request.RaceParticipantCheckInRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationReviewRequest;
import com.minhthien.hoser_backend.dto.request.RaceRegistrationWithdrawRequest;
import com.minhthien.hoser_backend.dto.request.RaceViolationRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.JockeyChallengeStandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceComplaintResponse;
import com.minhthien.hoser_backend.dto.response.RaceParticipantResponse;
import com.minhthien.hoser_backend.dto.response.RaceRegistrationResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResultResponse;
import com.minhthien.hoser_backend.dto.response.RaceViolationResponse;
import com.minhthien.hoser_backend.dto.response.RefereeRacePaymentResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import com.minhthien.hoser_backend.service.RaceDayService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
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
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn",
        "https://api.horseracing.id.vn"
})
public class RaceDayController {
    private final RaceDayService raceDayService;
    private final RefereePaymentService refereePaymentService;

    @PostMapping("/races/{id}/registrations")
    public ResponseEntity<ApiResponse<RaceRegistrationResponse>> registerForRace(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RaceRegistrationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race registration created",
                raceDayService.registerForRace(currentUser.getId(), id, request)));
    }

    @GetMapping("/owner/race-registrations")
    public ResponseEntity<ApiResponse<List<RaceRegistrationResponse>>> getOwnerRaceRegistrations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getOwnerRaceRegistrations(currentUser.getId())));
    }

    @PutMapping("/owner/race-registrations/{id}/withdraw")
    public ResponseEntity<ApiResponse<RaceRegistrationResponse>> withdrawRaceRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RaceRegistrationWithdrawRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race registration withdrawn",
                raceDayService.withdrawRaceRegistration(currentUser.getId(), id, request)));
    }

    @GetMapping("/admin/tournaments/{id}/race-registrations")
    public ResponseEntity<ApiResponse<List<RaceRegistrationResponse>>> getAdminTournamentRaceRegistrations(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getAdminTournamentRaceRegistrations(currentUser.getId(), id)));
    }

    @PutMapping("/admin/race-registrations/{id}/approve")
    public ResponseEntity<ApiResponse<RaceRegistrationResponse>> approveRaceRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RaceRegistrationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race registration approved",
                raceDayService.approveRaceRegistration(currentUser.getId(), id, request)));
    }

    @PutMapping("/admin/race-registrations/{id}/reject")
    public ResponseEntity<ApiResponse<RaceRegistrationResponse>> rejectRaceRegistration(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RaceRegistrationReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race registration rejected",
                raceDayService.rejectRaceRegistration(currentUser.getId(), id, request)));
    }

    @PutMapping("/admin/tournaments/{id}/schedule")
    public ResponseEntity<ApiResponse<TournamentResponse>> scheduleTournament(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Tournament scheduled",
                raceDayService.scheduleTournament(currentUser.getId(), id)));
    }

    @GetMapping("/admin/races/{id}/participants")
    public ResponseEntity<ApiResponse<List<RaceParticipantResponse>>> getRaceParticipants(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getRaceParticipants(currentUser.getId(), id)));
    }

    @GetMapping("/admin/races/{id}/violations")
    public ResponseEntity<ApiResponse<List<RaceViolationResponse>>> getAdminRaceViolations(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getAdminRaceViolations(currentUser.getId(), id)));
    }

    @PutMapping("/admin/races/{id}/cancel")
    public ResponseEntity<ApiResponse<RaceResponse>> cancelRace(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RaceCancellationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race cancelled",
                raceDayService.cancelRace(currentUser.getId(), id, request)));
    }

    @GetMapping("/admin/races/{id}/referee-payment")
    public ResponseEntity<ApiResponse<RefereeRacePaymentResponse>> getAdminRaceRefereePayment(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                refereePaymentService.getAdminRacePayment(currentUser.getId(), id)));
    }

    @GetMapping("/referee/races")
    public ResponseEntity<ApiResponse<List<RaceResponse>>> getRefereeRaces(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getRefereeRaces(currentUser.getId())));
    }

    @GetMapping("/referee/races/today")
    public ResponseEntity<ApiResponse<List<RaceResponse>>> getTodayRefereeRaces(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getTodayRefereeRaces(currentUser.getId())));
    }

    @GetMapping("/referee/payments")
    public ResponseEntity<ApiResponse<List<RefereeRacePaymentResponse>>> getRefereePayments(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                refereePaymentService.getRefereePayments(currentUser.getId())));
    }

    @GetMapping("/referee/races/{id}/participants")
    public ResponseEntity<ApiResponse<List<RaceParticipantResponse>>> getRefereeRaceParticipants(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getRefereeRaceParticipants(currentUser.getId(), id)));
    }

    @GetMapping("/referee/violations")
    public ResponseEntity<ApiResponse<List<RaceViolationResponse>>> getRefereeViolations(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getRefereeViolations(currentUser.getId())));
    }

    @GetMapping("/referee/races/{id}/violations")
    public ResponseEntity<ApiResponse<List<RaceViolationResponse>>> getRefereeRaceViolations(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getRefereeRaceViolations(currentUser.getId(), id)));
    }

    @PostMapping(value = "/referee/races/{id}/violations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RaceViolationResponse>> createRaceViolation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestPart("request") RaceViolationRequest request,
            @RequestPart("evidence") MultipartFile evidence) {
        return ResponseEntity.ok(ApiResponse.success("Race violation created",
                raceDayService.createRaceViolation(currentUser.getId(), id, request, evidence)));
    }

    @PutMapping(value = "/referee/races/{id}/violations/{violationId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RaceViolationResponse>> updateRaceViolation(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @PathVariable Long violationId,
            @Valid @RequestPart("request") RaceViolationRequest request,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return ResponseEntity.ok(ApiResponse.success("Race violation updated",
                raceDayService.updateRaceViolation(currentUser.getId(), id, violationId, request, evidence)));
    }

    @PutMapping("/referee/races/{id}/participants/{participantId}/gate")
    public ResponseEntity<ApiResponse<RaceParticipantResponse>> updateRefereeParticipantGate(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @PathVariable Long participantId,
            @Valid @RequestBody RaceGateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Participant gate updated",
                raceDayService.updateRefereeParticipantGate(currentUser.getId(), id, participantId, request)));
    }

    @PutMapping("/referee/races/{id}/participants/{participantId}/check-in")
    public ResponseEntity<ApiResponse<RaceParticipantResponse>> checkInRaceParticipant(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @PathVariable Long participantId,
            @Valid @RequestBody RaceParticipantCheckInRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race participant checked in",
                raceDayService.checkInRaceParticipant(currentUser.getId(), id, participantId, request)));
    }

    @PutMapping("/referee/races/{id}/start")
    public ResponseEntity<ApiResponse<RaceResponse>> startRace(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Race started",
                raceDayService.startRace(currentUser.getId(), id)));
    }

    @PostMapping("/referee/races/{id}/results/finalize")
    public ResponseEntity<ApiResponse<List<RaceResultResponse>>> finalizeRaceResult(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RaceFinalizeResultRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race result finalized",
                raceDayService.finalizeRaceResult(currentUser.getId(), id, request)));
    }

    @GetMapping("/races/{id}/results")
    public ResponseEntity<ApiResponse<List<RaceResultResponse>>> getRaceResults(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(raceDayService.getRaceResults(id)));
    }

    @PostMapping(value = "/races/{id}/complaints", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RaceComplaintResponse>> createRaceComplaint(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestPart("request") RaceComplaintRequest request,
            @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return ResponseEntity.ok(ApiResponse.success("Race complaint created",
                raceDayService.createRaceComplaint(currentUser.getId(), id, request, evidence)));
    }

    @GetMapping("/owner/race-complaints")
    public ResponseEntity<ApiResponse<List<RaceComplaintResponse>>> getOwnerRaceComplaints(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getOwnerRaceComplaints(currentUser.getId())));
    }

    @GetMapping("/admin/race-complaints")
    public ResponseEntity<ApiResponse<List<RaceComplaintResponse>>> getAdminRaceComplaints(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) RaceComplaintStatus status) {
        return ResponseEntity.ok(ApiResponse.success(
                raceDayService.getAdminRaceComplaints(currentUser.getId(), status)));
    }

    @PutMapping("/admin/race-complaints/{id}/resolve")
    public ResponseEntity<ApiResponse<RaceComplaintResponse>> resolveRaceComplaint(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RaceComplaintResolveRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Race complaint resolved",
                raceDayService.resolveRaceComplaint(currentUser.getId(), id, request)));
    }

    @PutMapping("/admin/tournaments/{id}/jockey-challenge/finalize")
    public ResponseEntity<ApiResponse<List<JockeyChallengeStandingResponse>>> finalizeJockeyChallenge(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Jockey challenge finalized",
                raceDayService.finalizeJockeyChallenge(currentUser.getId(), id)));
    }

    @GetMapping("/tournaments/{id}/jockey-challenge")
    public ResponseEntity<ApiResponse<List<JockeyChallengeStandingResponse>>> getJockeyChallengeStandings(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(raceDayService.getJockeyChallengeStandings(id)));
    }
}
