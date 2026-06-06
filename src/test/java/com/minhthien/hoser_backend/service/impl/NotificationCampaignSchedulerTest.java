package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.repository.NotificationCampaignRepository;
import com.minhthien.hoser_backend.service.NotificationCampaignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignSchedulerTest {
    @Mock private NotificationCampaignRepository campaignRepository;
    @Mock private NotificationCampaignService campaignService;

    @InjectMocks
    private NotificationCampaignScheduler scheduler;

    @Test
    void processesEveryDueScheduledCampaignInBatch() {
        when(campaignRepository.findDueIds(
                eq(NotificationCampaignStatus.SCHEDULED),
                any(LocalDateTime.class),
                any(Pageable.class))).thenReturn(List.of(4L, 8L));

        scheduler.processDueCampaigns();

        verify(campaignService).processCampaign(4L);
        verify(campaignService).processCampaign(8L);
    }
}
