package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.InvitationDecisionRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.response.JockeyInvitationResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.JockeyInvitationService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JockeyInvitationServiceImpl implements JockeyInvitationService {
    private static final String JOCKEY_INVITATION_REFERENCE = "JOCKEY_INVITATION";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private static final Set<AssignmentStatus> ACTIVE_STATUSES = Set.of(
            AssignmentStatus.PENDING,
            AssignmentStatus.ACCEPTED
    );

    private final JockeyInvitationRepository jockeyInvitationRepository;
    private final HorseRepository horseRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final FinanceSettingsService financeSettingsService;
    private NotificationService notificationService;

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public JockeyInvitationResponse createInvitation(Long ownerId, JockeyInvitationRequest request) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can create jockey invitations");
        Horse horse = horseRepository.findByIdAndOwnerId(request.getHorseId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Horse", "id", request.getHorseId()));
        if (horse.getStatus() != HorseStatus.APPROVED) {
            throw new BadRequestException("Horse must be approved before inviting a jockey");
        }

        User jockey = requireUser(request.getJockeyId());
        requireRole(jockey, UserRole.JOCKEY, "Invitation target must be a jockey");
        JockeyProfile profile = jockeyProfileRepository.findByUserId(jockey.getId())
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", jockey.getId()));
        if (profile.getStatus() != JockeyStatus.APPROVED) {
            throw new BadRequestException("Jockey profile must be approved before invitation");
        }
        if (jockeyInvitationRepository.existsByHorseIdAndJockeyIdAndStatusIn(
                horse.getId(), jockey.getId(), ACTIVE_STATUSES)) {
            throw new DuplicateResourceException("Active invitation already exists for this horse and jockey");
        }

        BigDecimal hirePrice = requireHirePrice(profile);
        BigDecimal taxPercent = financeSettingsService.getJockeyHireTaxPercent();
        BigDecimal taxAmount = calculateTaxAmount(hirePrice, taxPercent);
        BigDecimal jockeyPayoutAmount = hirePrice.subtract(taxAmount);

        JockeyInvitation invitation = JockeyInvitation.builder()
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .jockeyProfile(profile)
                .status(AssignmentStatus.PENDING)
                .message(request.getMessage())
                .hirePrice(hirePrice)
                .taxPercent(taxPercent)
                .taxAmount(taxAmount)
                .jockeyPayoutAmount(jockeyPayoutAmount)
                .createdBy(owner.getUsername())
                .updatedBy(owner.getUsername())
                .build();
        invitation = jockeyInvitationRepository.save(invitation);
        holdHireFee(invitation);
        invitation.setFundsHeldAt(LocalDateTime.now());
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getJockey(), NotificationType.INVITATION_CREATED,
                "New jockey invitation",
                "You received a jockey invitation for horse " + invitation.getHorse().getName(),
                invitation);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyInvitationResponse> getOwnerInvitations(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view owner invitations");
        return jockeyInvitationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyInvitationResponse getOwnerInvitation(Long ownerId, Long invitationId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view owner invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Cannot view another owner's invitation");
        }
        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyInvitationResponse> getOwnerAcceptedJockeys(Long ownerId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can view owner jockeys");
        return jockeyInvitationRepository.findByOwnerIdAndStatusOrderByCreatedAtDesc(
                        ownerId, AssignmentStatus.ACCEPTED).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public JockeyInvitationResponse cancelInvitation(Long ownerId, Long invitationId) {
        User owner = requireUser(ownerId);
        requireRole(owner, UserRole.OWNER, "Only owners can cancel owner invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        if (!invitation.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Cannot cancel another owner's invitation");
        }
        if (invitation.getStatus() != AssignmentStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be cancelled");
        }

        releaseHireFee(invitation, "Jockey invitation cancelled");
        invitation.setStatus(AssignmentStatus.CANCELLED);
        invitation.setCancelledAt(LocalDateTime.now());
        invitation.setUpdatedBy(owner.getUsername());
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getJockey(), NotificationType.INVITATION_CANCELLED,
                "Jockey invitation cancelled",
                "The invitation for horse " + invitation.getHorse().getName() + " was cancelled",
                invitation);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyInvitationResponse> getJockeyInvitations(Long jockeyId) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can view jockey invitations");
        return jockeyInvitationRepository.findByJockeyIdOrderByCreatedAtDesc(jockeyId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyInvitationResponse getJockeyInvitation(Long jockeyId, Long invitationId) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can view jockey invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        requireInvitationOwner(invitation, jockeyId);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional
    public JockeyInvitationResponse acceptInvitation(Long jockeyId, Long invitationId, InvitationDecisionRequest request) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can respond to jockey invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        requireInvitationOwner(invitation, jockeyId);
        requirePending(invitation);
        requireStillEligible(invitation);

        captureAndDistributeHireFee(invitation);
        invitation.setStatus(AssignmentStatus.ACCEPTED);
        invitation.setResponseNote(resolveNote(request));
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setPaidAt(LocalDateTime.now());
        invitation.setUpdatedBy(jockey.getUsername());
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getOwner(), NotificationType.INVITATION_ACCEPTED,
                "Jockey invitation accepted",
                invitation.getJockey().getUsername() + " accepted your invitation",
                invitation);
        return mapToResponse(invitation);
    }

    @Override
    @Transactional
    public JockeyInvitationResponse rejectInvitation(Long jockeyId, Long invitationId, InvitationDecisionRequest request) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can respond to jockey invitations");
        JockeyInvitation invitation = requireInvitation(invitationId);
        requireInvitationOwner(invitation, jockeyId);
        requirePending(invitation);

        releaseHireFee(invitation, "Jockey invitation rejected");
        invitation.setStatus(AssignmentStatus.REJECTED);
        invitation.setResponseNote(resolveNote(request));
        invitation.setRespondedAt(LocalDateTime.now());
        invitation.setUpdatedBy(jockey.getUsername());
        invitation = jockeyInvitationRepository.save(invitation);
        notify(invitation.getOwner(), NotificationType.INVITATION_REJECTED,
                "Jockey invitation rejected",
                invitation.getJockey().getUsername() + " rejected your invitation",
                invitation);
        return mapToResponse(invitation);
    }

    private void notify(User recipient, NotificationType type, String title, String message,
                        JockeyInvitation invitation) {
        if (notificationService == null) {
            return;
        }
        notificationService.notify(recipient, type, title, message, JOCKEY_INVITATION_REFERENCE,
                String.valueOf(invitation.getId()),
                "{\"horseId\":%d,\"jockeyId\":%d,\"ownerId\":%d}".formatted(
                        invitation.getHorse().getId(), invitation.getJockey().getId(), invitation.getOwner().getId()));
    }

    private void requireStillEligible(JockeyInvitation invitation) {
        if (invitation.getHorse().getStatus() != HorseStatus.APPROVED) {
            throw new BadRequestException("Horse is no longer approved");
        }
        if (invitation.getJockeyProfile().getStatus() != JockeyStatus.APPROVED) {
            throw new BadRequestException("Jockey profile is no longer approved");
        }
    }

    private void requireInvitationOwner(JockeyInvitation invitation, Long jockeyId) {
        if (!invitation.getJockey().getId().equals(jockeyId)) {
            throw new UnauthorizedException("Cannot respond to another jockey's invitation");
        }
    }

    private void requirePending(JockeyInvitation invitation) {
        if (invitation.getStatus() != AssignmentStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be updated");
        }
    }

    private String resolveNote(InvitationDecisionRequest request) {
        return request == null ? null : request.getNote();
    }

    private BigDecimal requireHirePrice(JockeyProfile profile) {
        BigDecimal hirePrice = profile.getHirePrice();
        if (hirePrice == null || hirePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Jockey hire price must be configured before invitation");
        }
        return hirePrice;
    }

    private BigDecimal calculateTaxAmount(BigDecimal hirePrice, BigDecimal taxPercent) {
        return hirePrice.multiply(taxPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private void holdHireFee(JockeyInvitation invitation) {
        walletService.hold(
                invitation.getOwner().getId(),
                invitation.getHirePrice(),
                com.minhthien.hoser_backend.enums.WalletTransactionType.JOCKEY_HIRE,
                JOCKEY_INVITATION_REFERENCE,
                invitation.getId().toString(),
                idempotencyKey(invitation, "hold"),
                hireMetadata(invitation),
                "Hold jockey hire fee"
        );
    }

    private void releaseHireFee(JockeyInvitation invitation, String note) {
        requireHeldHireFee(invitation);
        walletService.release(
                invitation.getOwner().getId(),
                invitation.getHirePrice(),
                com.minhthien.hoser_backend.enums.WalletTransactionType.JOCKEY_HIRE,
                JOCKEY_INVITATION_REFERENCE,
                invitation.getId().toString(),
                idempotencyKey(invitation, "release"),
                hireMetadata(invitation),
                note
        );
    }

    private void captureAndDistributeHireFee(JockeyInvitation invitation) {
        requireHeldHireFee(invitation);
        walletService.capture(
                invitation.getOwner().getId(),
                invitation.getHirePrice(),
                com.minhthien.hoser_backend.enums.WalletTransactionType.JOCKEY_HIRE,
                JOCKEY_INVITATION_REFERENCE,
                invitation.getId().toString(),
                idempotencyKey(invitation, "capture"),
                hireMetadata(invitation),
                "Capture jockey hire fee"
        );
        walletService.credit(
                invitation.getJockey().getId(),
                invitation.getJockeyPayoutAmount(),
                com.minhthien.hoser_backend.enums.WalletTransactionType.JOCKEY_PAYOUT,
                JOCKEY_INVITATION_REFERENCE,
                invitation.getId().toString(),
                idempotencyKey(invitation, "jockey-payout"),
                hireMetadata(invitation),
                "Jockey hire payout"
        );
        if (invitation.getTaxAmount() != null && invitation.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            walletService.creditAdmin(
                    invitation.getTaxAmount(),
                    com.minhthien.hoser_backend.enums.WalletTransactionType.JOCKEY_HIRE_TAX,
                    JOCKEY_INVITATION_REFERENCE,
                    invitation.getId().toString(),
                    idempotencyKey(invitation, "admin-tax"),
                    hireMetadata(invitation),
                    "Jockey hire tax"
            );
        }
    }

    private void requireHeldHireFee(JockeyInvitation invitation) {
        if (invitation.getFundsHeldAt() == null || invitation.getHirePrice() == null
                || invitation.getHirePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Jockey invitation has no held hire payment");
        }
        if (invitation.getPaidAt() != null) {
            throw new BadRequestException("Jockey invitation has already been paid");
        }
    }

    private String idempotencyKey(JockeyInvitation invitation, String action) {
        return "jockey-invitation:" + invitation.getId() + ":" + action;
    }

    private String hireMetadata(JockeyInvitation invitation) {
        return "hirePrice=" + invitation.getHirePrice()
                + ";taxPercent=" + invitation.getTaxPercent()
                + ";taxAmount=" + invitation.getTaxAmount()
                + ";jockeyPayoutAmount=" + invitation.getJockeyPayoutAmount();
    }

    private JockeyInvitation requireInvitation(Long invitationId) {
        return jockeyInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyInvitation", "id", invitationId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) {
            throw new UnauthorizedException(message);
        }
    }

    private JockeyInvitationResponse mapToResponse(JockeyInvitation invitation) {
        return JockeyInvitationResponse.builder()
                .id(invitation.getId())
                .ownerId(invitation.getOwner().getId())
                .ownerUsername(invitation.getOwner().getUsername())
                .jockeyId(invitation.getJockey().getId())
                .jockeyUsername(invitation.getJockey().getUsername())
                .jockeyProfileId(invitation.getJockeyProfile().getId())
                .horseId(invitation.getHorse().getId())
                .horseName(invitation.getHorse().getName())
                .status(invitation.getStatus())
                .message(invitation.getMessage())
                .responseNote(invitation.getResponseNote())
                .hirePrice(invitation.getHirePrice())
                .taxPercent(invitation.getTaxPercent())
                .taxAmount(invitation.getTaxAmount())
                .jockeyPayoutAmount(invitation.getJockeyPayoutAmount())
                .fundsHeldAt(invitation.getFundsHeldAt())
                .paidAt(invitation.getPaidAt())
                .respondedAt(invitation.getRespondedAt())
                .cancelledAt(invitation.getCancelledAt())
                .createdAt(invitation.getCreatedAt())
                .updatedAt(invitation.getUpdatedAt())
                .build();
    }
}
