package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.NotificationCampaignRequest;
import com.minhthien.hoser_backend.dto.response.NotificationResponse;
import com.minhthien.hoser_backend.entity.NotificationCampaign;
import com.minhthien.hoser_backend.entity.NotificationDelivery;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.NotificationCampaignRepository;
import com.minhthien.hoser_backend.repository.NotificationDeliveryRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCampaignServiceImplTest {
    @Mock private NotificationCampaignRepository campaignRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private MailService mailService;

    @InjectMocks
    private NotificationCampaignServiceImpl service;

    @Test
    void createAllAudienceSnapshotsActiveNonAdminRecipientsForEveryChannel() {
        User admin = user(1L, UserRole.ADMIN, "admin@example.com");
        User owner = user(2L, UserRole.OWNER, "owner@example.com");
        User jockey = user(3L, UserRole.JOCKEY, "jockey@example.com");
        NotificationCampaignRequest request = request(
                NotificationAudienceType.ALL, null, Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByActiveAndRoleNotOrderByIdAsc(true, UserRole.ADMIN))
                .thenReturn(List.of(owner, jockey));
        when(campaignRepository.save(any(NotificationCampaign.class))).thenAnswer(invocation -> {
            NotificationCampaign campaign = invocation.getArgument(0);
            campaign.setId(10L);
            return campaign;
        });

        var response = service.createCampaign(1L, request);

        assertEquals(2, response.getRecipientCount());
        assertEquals(NotificationCampaignStatus.SCHEDULED, response.getStatus());
        ArgumentCaptor<List<NotificationDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        assertEquals(4, captor.getValue().size());
        assertEquals(Set.of(owner, jockey), captor.getValue().stream()
                .map(NotificationDelivery::getRecipient).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH), captor.getValue().stream()
                .map(NotificationDelivery::getChannel).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void createRoleAudienceOnlyLoadsRequestedActiveRole() {
        User admin = user(1L, UserRole.ADMIN, "admin@example.com");
        User referee = user(4L, UserRole.REFEREE, "referee@example.com");
        NotificationCampaignRequest request = request(
                NotificationAudienceType.ROLE, UserRole.REFEREE, Set.of(NotificationChannel.PUSH));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findByActiveAndRoleOrderByIdAsc(true, UserRole.REFEREE))
                .thenReturn(List.of(referee));
        when(campaignRepository.save(any(NotificationCampaign.class))).thenAnswer(invocation -> {
            NotificationCampaign campaign = invocation.getArgument(0);
            campaign.setId(11L);
            return campaign;
        });

        var response = service.createCampaign(1L, request);

        assertEquals(1, response.getRecipientCount());
        verify(userRepository, never()).findByActiveAndRoleNotOrderByIdAsc(anyBoolean(), any());
    }

    @Test
    void invalidAudienceCombinationsAreRejected() {
        NotificationCampaignRequest allWithRole = request(
                NotificationAudienceType.ALL, UserRole.OWNER, Set.of(NotificationChannel.PUSH));
        NotificationCampaignRequest roleWithoutRole = request(
                NotificationAudienceType.ROLE, null, Set.of(NotificationChannel.PUSH));
        NotificationCampaignRequest adminRole = request(
                NotificationAudienceType.ROLE, UserRole.ADMIN, Set.of(NotificationChannel.PUSH));

        assertThrows(BadRequestException.class, () -> service.createCampaign(1L, allWithRole));
        assertThrows(BadRequestException.class, () -> service.createCampaign(1L, roleWithoutRole));
        assertThrows(BadRequestException.class, () -> service.createCampaign(1L, adminRole));
        verifyNoInteractions(campaignRepository, deliveryRepository);
    }

    @Test
    void emailFailureDoesNotBlockPushAndCampaignBecomesPartialFailed() {
        User recipient = user(2L, UserRole.OWNER, "owner@example.com");
        NotificationCampaign campaign = campaign(20L, NotificationCampaignStatus.SCHEDULED,
                Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH));
        NotificationDelivery email = delivery(1L, campaign, recipient, NotificationChannel.EMAIL);
        NotificationDelivery push = delivery(2L, campaign, recipient, NotificationChannel.PUSH);
        when(campaignRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(campaign));
        when(deliveryRepository.findByCampaignIdAndStatusOrderByIdAsc(
                20L, NotificationDeliveryStatus.PENDING)).thenReturn(List.of(email, push));
        doThrow(new IllegalStateException("SMTP unavailable")).when(mailService)
                .sendAnnouncement(eq(recipient), anyString(), anyString(), anyString(), anyString());
        when(notificationService.notify(eq(recipient), eq(NotificationType.ADMIN_ANNOUNCEMENT),
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(NotificationResponse.builder().id(99L).build());
        when(deliveryRepository.countByCampaignIdAndChannelAndStatus(anyLong(), any(), any()))
                .thenAnswer(invocation -> {
                    NotificationChannel channel = invocation.getArgument(1);
                    NotificationDeliveryStatus status = invocation.getArgument(2);
                    return channel == NotificationChannel.PUSH && status == NotificationDeliveryStatus.SENT ? 1L
                            : channel == NotificationChannel.EMAIL && status == NotificationDeliveryStatus.FAILED ? 1L
                            : 0L;
                });

        service.processCampaign(20L);

        assertEquals(NotificationDeliveryStatus.FAILED, email.getStatus());
        assertEquals("SMTP unavailable", email.getErrorMessage());
        assertEquals(NotificationDeliveryStatus.SENT, push.getStatus());
        assertEquals(NotificationCampaignStatus.PARTIAL_FAILED, campaign.getStatus());
        assertNotNull(campaign.getCompletedAt());
    }

    @Test
    void invalidEmailIsSkippedWithoutCallingMailProvider() {
        User recipient = user(2L, UserRole.OWNER, "not-an-email");
        NotificationCampaign campaign = campaign(
                21L, NotificationCampaignStatus.SCHEDULED, Set.of(NotificationChannel.EMAIL));
        NotificationDelivery email = delivery(1L, campaign, recipient, NotificationChannel.EMAIL);
        when(campaignRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(campaign));
        when(deliveryRepository.findByCampaignIdAndStatusOrderByIdAsc(
                21L, NotificationDeliveryStatus.PENDING)).thenReturn(List.of(email));
        when(deliveryRepository.countByCampaignIdAndChannelAndStatus(anyLong(), any(), any()))
                .thenAnswer(invocation ->
                        invocation.getArgument(2) == NotificationDeliveryStatus.SKIPPED ? 1L : 0L);

        service.processCampaign(21L);

        assertEquals(NotificationDeliveryStatus.SKIPPED, email.getStatus());
        assertEquals(NotificationCampaignStatus.FAILED, campaign.getStatus());
        verifyNoInteractions(mailService);
    }

    @Test
    void futureAndAlreadyFinishedCampaignsDoNotSend() {
        NotificationCampaign future = campaign(
                30L, NotificationCampaignStatus.SCHEDULED, Set.of(NotificationChannel.PUSH));
        future.setScheduledAt(LocalDateTime.now().plusHours(1));
        NotificationCampaign finished = campaign(
                31L, NotificationCampaignStatus.COMPLETED, Set.of(NotificationChannel.PUSH));
        when(campaignRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(future));
        when(campaignRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(finished));

        service.processCampaign(30L);
        service.processCampaign(31L);

        verify(deliveryRepository, never())
                .findByCampaignIdAndStatusOrderByIdAsc(anyLong(), any());
        verifyNoInteractions(notificationService, mailService);
    }

    private NotificationCampaignRequest request(
            NotificationAudienceType audienceType,
            UserRole audienceRole,
            Set<NotificationChannel> channels) {
        NotificationCampaignRequest request = new NotificationCampaignRequest();
        request.setTitle("Race notice");
        request.setContent("Please check in on time.");
        request.setAudienceType(audienceType);
        request.setAudienceRole(audienceRole);
        request.setChannels(channels);
        return request;
    }

    private User user(Long id, UserRole role, String email) {
        return User.builder()
                .id(id)
                .username("user-" + id)
                .email(email)
                .role(role)
                .active(true)
                .build();
    }

    private NotificationCampaign campaign(
            Long id,
            NotificationCampaignStatus status,
            Set<NotificationChannel> channels) {
        return NotificationCampaign.builder()
                .id(id)
                .title("Race notice")
                .content("Please check in on time.")
                .audienceType(NotificationAudienceType.ALL)
                .channels(new LinkedHashSet<>(channels))
                .scheduledAt(LocalDateTime.now().minusMinutes(1))
                .status(status)
                .createdBy(user(1L, UserRole.ADMIN, "admin@example.com"))
                .recipientCount(1)
                .build();
    }

    private NotificationDelivery delivery(
            Long id,
            NotificationCampaign campaign,
            User recipient,
            NotificationChannel channel) {
        return NotificationDelivery.builder()
                .id(id)
                .campaign(campaign)
                .recipient(recipient)
                .channel(channel)
                .status(NotificationDeliveryStatus.PENDING)
                .build();
    }
}
