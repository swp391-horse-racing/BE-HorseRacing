package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.repository.NotificationCampaignRepository;
import com.minhthien.hoser_backend.service.NotificationCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCampaignScheduler {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int BATCH_SIZE = 20;

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationCampaignService campaignService;

    @Scheduled(
            initialDelayString = "${app.notification-campaign.initial-delay-ms:30000}",
            fixedDelayString = "${app.notification-campaign.delay-ms:30000}"
    )
    public void processDueCampaigns() {
        List<Long> campaignIds = campaignRepository.findDueIds(
                NotificationCampaignStatus.SCHEDULED,
                LocalDateTime.now(BUSINESS_ZONE),
                PageRequest.of(0, BATCH_SIZE));
        for (Long campaignId : campaignIds) {
            try {
                campaignService.processCampaign(campaignId);
            } catch (RuntimeException ex) {
                log.error("Could not process notification campaign: campaignId={}", campaignId, ex);
            }
        }
    }
}
