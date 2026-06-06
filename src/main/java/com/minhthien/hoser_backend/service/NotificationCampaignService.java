package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.NotificationCampaignRequest;
import com.minhthien.hoser_backend.dto.response.NotificationAudienceCountResponse;
import com.minhthien.hoser_backend.dto.response.NotificationCampaignResponse;
import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import com.minhthien.hoser_backend.enums.UserRole;
import org.springframework.data.domain.Page;

public interface NotificationCampaignService {
    NotificationCampaignResponse createCampaign(Long adminId, NotificationCampaignRequest request);

    NotificationCampaignResponse getCampaign(Long id);

    Page<NotificationCampaignResponse> getCampaigns(
            NotificationCampaignStatus status,
            NotificationChannel channel,
            NotificationAudienceType audienceType,
            int page,
            int size);

    NotificationAudienceCountResponse getAudienceCount(
            NotificationAudienceType audienceType,
            UserRole audienceRole);

    void processCampaign(Long campaignId);

    boolean isDue(NotificationCampaignResponse campaign);
}
