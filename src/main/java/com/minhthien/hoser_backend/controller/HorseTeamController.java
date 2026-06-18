package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.EligibleHorseTeamResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.HorseTeamService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn"
})
public class HorseTeamController {
    private final HorseTeamService horseTeamService;

    @GetMapping("/owner/horse-teams/eligible")
    public ResponseEntity<ApiResponse<List<EligibleHorseTeamResponse>>> getOwnerEligibleHorseTeams(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                horseTeamService.getOwnerEligibleHorseTeams(currentUser.getId())));
    }

    @GetMapping("/admin/tournaments/{id}/eligible-horse-teams")
    public ResponseEntity<ApiResponse<List<EligibleHorseTeamResponse>>> getAdminTournamentEligibleHorseTeams(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                horseTeamService.getAdminTournamentEligibleHorseTeams(currentUser.getId(), id)));
    }
}
