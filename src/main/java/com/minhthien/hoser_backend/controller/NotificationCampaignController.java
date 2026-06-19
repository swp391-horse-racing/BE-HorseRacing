package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.NotificationCampaignRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.NotificationAudienceCountResponse;
import com.minhthien.hoser_backend.dto.response.NotificationCampaignResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.service.NotificationCampaignService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notification-campaigns")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://localhost:51093",
        "https://horseracing.id.vn",
        "https://www.horseracing.id.vn"
})
public class NotificationCampaignController {
    private final NotificationCampaignService campaignService;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> createCampaign(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody NotificationCampaignRequest request) {
        NotificationCampaignResponse campaign = campaignService.createCampaign(currentUser.getId(), request);
        if (campaignService.isDue(campaign)) {
            campaignService.processCampaign(campaign.getId());
            campaign = campaignService.getCampaign(campaign.getId());
        }
        return ResponseEntity.ok(ApiResponse.success("Notification campaign created", campaign));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationCampaignResponse>>> getCampaigns(
            @RequestParam(required = false) NotificationCampaignStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationAudienceType audienceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                campaignService.getCampaigns(status, channel, audienceType, page, size)));
    }

    @GetMapping("/audience-count")
    public ResponseEntity<ApiResponse<NotificationAudienceCountResponse>> getAudienceCount(
            @RequestParam NotificationAudienceType audienceType,
            @RequestParam(required = false) UserRole audienceRole) {
        return ResponseEntity.ok(ApiResponse.success(
                campaignService.getAudienceCount(audienceType, audienceRole)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationCampaignResponse>> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(campaignService.getCampaign(id)));
    }
}
