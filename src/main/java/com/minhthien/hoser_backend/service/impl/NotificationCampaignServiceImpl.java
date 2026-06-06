package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.NotificationCampaignRequest;
import com.minhthien.hoser_backend.dto.response.NotificationAudienceCountResponse;
import com.minhthien.hoser_backend.dto.response.NotificationCampaignResponse;
import com.minhthien.hoser_backend.dto.response.NotificationChannelStatsResponse;
import com.minhthien.hoser_backend.entity.NotificationCampaign;
import com.minhthien.hoser_backend.entity.NotificationDelivery;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.NotificationCampaignRepository;
import com.minhthien.hoser_backend.repository.NotificationDeliveryRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationCampaignService;
import com.minhthien.hoser_backend.service.NotificationService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCampaignServiceImpl implements NotificationCampaignService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String CAMPAIGN_REFERENCE_TYPE = "NOTIFICATION_CAMPAIGN";
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationCampaignRepository campaignRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Override
    @Transactional
    public NotificationCampaignResponse createCampaign(Long adminId, NotificationCampaignRequest request) {
        validateAudience(request.getAudienceType(), request.getAudienceRole());
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Only admins can create notification campaigns");
        }

        LocalDateTime now = now();
        List<User> recipients = recipients(request.getAudienceType(), request.getAudienceRole());
        NotificationCampaign campaign = campaignRepository.save(NotificationCampaign.builder()
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .audienceType(request.getAudienceType())
                .audienceRole(request.getAudienceRole())
                .channels(new LinkedHashSet<>(request.getChannels()))
                .scheduledAt(request.getScheduledAt() == null ? now : request.getScheduledAt())
                .status(NotificationCampaignStatus.SCHEDULED)
                .createdBy(admin)
                .recipientCount(recipients.size())
                .build());

        List<NotificationDelivery> deliveries = new ArrayList<>(
                recipients.size() * campaign.getChannels().size());
        for (User recipient : recipients) {
            for (NotificationChannel channel : campaign.getChannels()) {
                deliveries.add(NotificationDelivery.builder()
                        .campaign(campaign)
                        .recipient(recipient)
                        .channel(channel)
                        .build());
            }
        }
        deliveryRepository.saveAll(deliveries);
        return map(campaign, pendingStats(campaign.getChannels(), recipients.size()));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationCampaignResponse getCampaign(Long id) {
        NotificationCampaign campaign = campaignRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification campaign", "id", id));
        return map(campaign, stats(id, campaign.getChannels()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationCampaignResponse> getCampaigns(
            NotificationCampaignStatus status,
            NotificationChannel channel,
            NotificationAudienceType audienceType,
            int page,
            int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return campaignRepository.findFiltered(status, channel, audienceType, pageable)
                .map(campaign -> map(campaign, stats(campaign.getId(), campaign.getChannels())));
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationAudienceCountResponse getAudienceCount(
            NotificationAudienceType audienceType,
            UserRole audienceRole) {
        validateAudience(audienceType, audienceRole);
        long count = audienceType == NotificationAudienceType.ALL
                ? userRepository.countByActiveAndRoleNot(true, UserRole.ADMIN)
                : userRepository.countByActiveAndRole(true, audienceRole);
        return new NotificationAudienceCountResponse(audienceType, audienceRole, count);
    }

    @Override
    @Transactional
    public void processCampaign(Long campaignId) {
        NotificationCampaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification campaign", "id", campaignId));
        if (campaign.getStatus() != NotificationCampaignStatus.SCHEDULED
                || campaign.getScheduledAt().isAfter(now())) {
            return;
        }

        campaign.setStatus(NotificationCampaignStatus.PROCESSING);
        campaign.setStartedAt(now());
        campaignRepository.save(campaign);

        List<NotificationDelivery> pending = deliveryRepository
                .findByCampaignIdAndStatusOrderByIdAsc(campaignId, NotificationDeliveryStatus.PENDING);
        for (NotificationDelivery delivery : pending) {
            processDelivery(campaign, delivery);
        }

        long sent = count(campaignId, campaign.getChannels(), NotificationDeliveryStatus.SENT);
        long failed = count(campaignId, campaign.getChannels(), NotificationDeliveryStatus.FAILED)
                + count(campaignId, campaign.getChannels(), NotificationDeliveryStatus.SKIPPED);
        campaign.setStatus(resolveFinalStatus(sent, failed));
        campaign.setCompletedAt(now());
        campaignRepository.save(campaign);
    }

    @Override
    public boolean isDue(NotificationCampaignResponse campaign) {
        return campaign.getStatus() == NotificationCampaignStatus.SCHEDULED
                && !campaign.getScheduledAt().isAfter(now());
    }

    private void processDelivery(NotificationCampaign campaign, NotificationDelivery delivery) {
        User recipient = delivery.getRecipient();
        try {
            if (delivery.getChannel() == NotificationChannel.EMAIL) {
                if (!hasValidEmail(recipient.getEmail())) {
                    mark(delivery, NotificationDeliveryStatus.SKIPPED, "Recipient email is missing or invalid");
                    return;
                }
                mailService.sendAnnouncement(
                        recipient,
                        campaign.getTitle(),
                        campaign.getContent(),
                        CAMPAIGN_REFERENCE_TYPE,
                        String.valueOf(campaign.getId()));
            } else {
                var notification = notificationService.notify(
                        recipient,
                        NotificationType.ADMIN_ANNOUNCEMENT,
                        campaign.getTitle(),
                        campaign.getContent(),
                        CAMPAIGN_REFERENCE_TYPE,
                        String.valueOf(campaign.getId()),
                        "{\"campaignId\":%d}".formatted(campaign.getId()));
                if (notification == null) {
                    mark(delivery, NotificationDeliveryStatus.FAILED, "Could not create push notification");
                    return;
                }
            }
            mark(delivery, NotificationDeliveryStatus.SENT, null);
        } catch (RuntimeException ex) {
            log.warn("Notification campaign delivery failed: campaignId={}, recipientId={}, channel={}",
                    campaign.getId(), recipient.getId(), delivery.getChannel(), ex);
            mark(delivery, NotificationDeliveryStatus.FAILED, errorMessage(ex));
        }
    }

    private void mark(NotificationDelivery delivery, NotificationDeliveryStatus status, String errorMessage) {
        delivery.setStatus(status);
        delivery.setErrorMessage(errorMessage);
        delivery.setSentAt(status == NotificationDeliveryStatus.SENT ? now() : null);
        deliveryRepository.save(delivery);
    }

    private NotificationCampaignStatus resolveFinalStatus(long sent, long failed) {
        if (failed == 0) {
            return NotificationCampaignStatus.COMPLETED;
        }
        if (sent == 0) {
            return NotificationCampaignStatus.FAILED;
        }
        return NotificationCampaignStatus.PARTIAL_FAILED;
    }

    private long count(
            Long campaignId,
            Set<NotificationChannel> channels,
            NotificationDeliveryStatus status) {
        return channels.stream()
                .mapToLong(channel -> deliveryRepository
                        .countByCampaignIdAndChannelAndStatus(campaignId, channel, status))
                .sum();
    }

    private List<User> recipients(NotificationAudienceType audienceType, UserRole audienceRole) {
        if (audienceType == NotificationAudienceType.ALL) {
            return userRepository.findByActiveAndRoleNotOrderByIdAsc(true, UserRole.ADMIN);
        }
        return userRepository.findByActiveAndRoleOrderByIdAsc(true, audienceRole);
    }

    private void validateAudience(NotificationAudienceType audienceType, UserRole audienceRole) {
        if (audienceType == null) {
            throw new BadRequestException("Audience type is required");
        }
        if (audienceType == NotificationAudienceType.ALL && audienceRole != null) {
            throw new BadRequestException("Audience role must be empty when audience type is ALL");
        }
        if (audienceType == NotificationAudienceType.ROLE) {
            if (audienceRole == null) {
                throw new BadRequestException("Audience role is required when audience type is ROLE");
            }
            if (audienceRole == UserRole.ADMIN) {
                throw new BadRequestException("ADMIN is not a supported campaign audience role");
            }
        }
    }

    private boolean hasValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(email);
            address.validate();
            return true;
        } catch (AddressException ex) {
            return false;
        }
    }

    private String errorMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private List<NotificationChannelStatsResponse> stats(
            Long campaignId,
            Set<NotificationChannel> channels) {
        Map<NotificationChannel, EnumMap<NotificationDeliveryStatus, Long>> counts = new EnumMap<>(
                NotificationChannel.class);
        for (Object[] row : deliveryRepository.summarizeByCampaignId(campaignId)) {
            NotificationChannel channel = (NotificationChannel) row[0];
            NotificationDeliveryStatus status = (NotificationDeliveryStatus) row[1];
            counts.computeIfAbsent(channel, ignored -> new EnumMap<>(NotificationDeliveryStatus.class))
                    .put(status, (Long) row[2]);
        }
        return channels.stream()
                .map(channel -> channelStats(channel, counts.get(channel)))
                .toList();
    }

    private List<NotificationChannelStatsResponse> pendingStats(
            Set<NotificationChannel> channels,
            long recipientCount) {
        return channels.stream()
                .map(channel -> NotificationChannelStatsResponse.builder()
                        .channel(channel)
                        .pendingCount(recipientCount)
                        .build())
                .toList();
    }

    private NotificationChannelStatsResponse channelStats(
            NotificationChannel channel,
            Map<NotificationDeliveryStatus, Long> counts) {
        Map<NotificationDeliveryStatus, Long> values = counts == null ? Map.of() : counts;
        return NotificationChannelStatsResponse.builder()
                .channel(channel)
                .pendingCount(values.getOrDefault(NotificationDeliveryStatus.PENDING, 0L))
                .sentCount(values.getOrDefault(NotificationDeliveryStatus.SENT, 0L))
                .failedCount(values.getOrDefault(NotificationDeliveryStatus.FAILED, 0L))
                .skippedCount(values.getOrDefault(NotificationDeliveryStatus.SKIPPED, 0L))
                .build();
    }

    private NotificationCampaignResponse map(
            NotificationCampaign campaign,
            List<NotificationChannelStatsResponse> channelStats) {
        return NotificationCampaignResponse.builder()
                .id(campaign.getId())
                .title(campaign.getTitle())
                .content(campaign.getContent())
                .audienceType(campaign.getAudienceType())
                .audienceRole(campaign.getAudienceRole())
                .channels(new LinkedHashSet<>(campaign.getChannels()))
                .status(campaign.getStatus())
                .recipientCount(campaign.getRecipientCount())
                .createdById(campaign.getCreatedBy().getId())
                .createdByUsername(campaign.getCreatedBy().getUsername())
                .scheduledAt(campaign.getScheduledAt())
                .createdAt(campaign.getCreatedAt())
                .startedAt(campaign.getStartedAt())
                .completedAt(campaign.getCompletedAt())
                .channelStats(channelStats)
                .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
