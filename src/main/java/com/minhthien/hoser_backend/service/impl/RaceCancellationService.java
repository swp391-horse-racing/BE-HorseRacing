package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.JockeyInvitationService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RefereeInvitationService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaceCancellationService {
    private static final String REGISTRATION_REFERENCE = "RACE_REGISTRATION";
    private static final String RACE_REFERENCE = "RACE";
    private static final String TOURNAMENT_REFERENCE = "TOURNAMENT";

    private final RaceRepository raceRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final WalletService walletService;
    private final BettingService bettingService;
    private final JockeyInvitationService jockeyInvitationService;
    private final RefereeInvitationService refereeInvitationService;
    private final RefereePaymentService refereePaymentService;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Transactional
    public RaceCancellationResult cancelRace(Long raceId, Long adminId, String reason,
                                             String updatedBy, boolean notifyRaceCancellation) {
        Race race = raceRepository.findByIdForUpdate(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
        if (race.getStatus() == RaceStatus.CANCELLED) {
            return new RaceCancellationResult(List.of());
        }

        String cancellationReason = reason == null || reason.isBlank()
                ? "The race was cancelled"
                : reason.trim();
        String actor = updatedBy == null || updatedBy.isBlank() ? "SYSTEM" : updatedBy;
        Map<Long, User> affectedUsers = new LinkedHashMap<>();
        List<RegistrationCancellationNotice> ownerNotices = new ArrayList<>();

        raceRegistrationRepository.findByRaceIdOrderByCreatedAtDesc(raceId).stream()
                .filter(this::isActive)
                .forEach(registration -> {
                    refundRegistrationFee(registration,
                            "Race entry fee refunded - " + cancellationReason);
                    registration.setStatus(RaceRegistrationStatus.CANCELLED);
                    registration.setReviewedBy(adminId);
                    registration.setReviewedAt(LocalDateTime.now());
                    registration.setReviewNote(cancellationReason);
                    raceRegistrationRepository.save(registration);

                    addUser(affectedUsers, registration.getOwner());
                    addUser(affectedUsers, registration.getJockey());
                    ownerNotices.add(new RegistrationCancellationNotice(
                            registration.getOwner(),
                            registration.getId(),
                            race.getName(),
                            cancellationReason + ". Your entry fee has been refunded."));
                });

        List<User> jockeys = jockeyInvitationService.cancelActiveInvitationsForRace(
                raceId, cancellationReason, actor);
        List<User> referees = refereeInvitationService.cancelActiveInvitationsForRace(
                raceId, cancellationReason, actor);
        jockeys.forEach(user -> addUser(affectedUsers, user));
        referees.forEach(user -> addUser(affectedUsers, user));
        addUser(affectedUsers, race.getReferee());

        bettingService.cancelRaceBets(raceId);
        refereePaymentService.releaseForCancelledRace(adminId, race);
        race.setStatus(RaceStatus.CANCELLED);
        race.setReferee(null);
        raceRepository.save(race);

        ownerNotices.forEach(this::notifyRegistrationCancelledAfterCommit);
        if (notifyRaceCancellation) {
            Map<Long, User> raceRecipients = new LinkedHashMap<>();
            jockeys.forEach(user -> addUser(raceRecipients, user));
            referees.forEach(user -> addUser(raceRecipients, user));
            affectedUsers.values().stream()
                    .filter(user -> user != null && user.getRole() != null
                            && (user.getRole() == UserRole.JOCKEY
                            || user.getRole() == UserRole.REFEREE))
                    .forEach(user -> addUser(raceRecipients, user));
            notifyRaceCancelledAfterCommit(race.getId(), race.getTournament().getId(),
                    race.getName(), cancellationReason, raceRecipients.values());
        }

        log.info("Cancelled race: id={}, reason={}", raceId, cancellationReason);
        return new RaceCancellationResult(List.copyOf(affectedUsers.values()));
    }

    public void notifyTournamentCancelledAfterCommit(Tournament tournament, Collection<User> recipients,
                                                     long approvedParticipants, int minimumTeams) {
        String reason = "Insufficient approved teams (%d/%d)"
                .formatted(approvedParticipants, minimumTeams);
        String metadata = "{\"tournamentId\":%d,\"status\":\"CANCELLED\",\"approvedTeams\":%d,\"minimumTeams\":%d}"
                .formatted(tournament.getId(), approvedParticipants, minimumTeams);
        uniqueUsers(recipients).forEach(recipient -> afterCommit(() -> {
            safeRun(() -> notificationService.notify(
                    recipient,
                    NotificationType.TOURNAMENT_CANCELLED,
                    "Tournament cancelled",
                    "Tournament " + tournament.getName() + " was cancelled. " + reason + ".",
                    TOURNAMENT_REFERENCE,
                    String.valueOf(tournament.getId()),
                    metadata),
                    "tournament cancellation notification", tournament.getId(), recipient.getId());
            safeRun(() -> mailService.sendTournamentCancelled(
                    recipient, tournament.getName(), reason,
                    TOURNAMENT_REFERENCE, String.valueOf(tournament.getId())),
                    "tournament cancellation email", tournament.getId(), recipient.getId());
        }));
    }

    private void notifyRegistrationCancelledAfterCommit(RegistrationCancellationNotice notice) {
        if (notice.owner() == null) {
            return;
        }
        afterCommit(() -> {
            safeRun(() -> notificationService.notify(
                    notice.owner(),
                    NotificationType.REGISTRATION_CANCELLED,
                    "Race registration cancelled",
                    "Your registration for race " + notice.raceName() + " was cancelled. " + notice.reason(),
                    REGISTRATION_REFERENCE,
                    String.valueOf(notice.registrationId()),
                    "{\"status\":\"CANCELLED\"}"),
                    "registration cancellation notification", notice.registrationId(), notice.owner().getId());
            safeRun(() -> mailService.sendRegistrationCancelled(
                    notice.owner(), notice.raceName(), notice.reason(),
                    REGISTRATION_REFERENCE, String.valueOf(notice.registrationId())),
                    "registration cancellation email", notice.registrationId(), notice.owner().getId());
        });
    }

    private void notifyRaceCancelledAfterCommit(Long raceId, Long tournamentId, String raceName,
                                                String reason, Collection<User> recipients) {
        String metadata = "{\"raceId\":%d,\"tournamentId\":%d,\"status\":\"CANCELLED\"}"
                .formatted(raceId, tournamentId);
        uniqueUsers(recipients).forEach(recipient -> afterCommit(() -> {
            safeRun(() -> notificationService.notify(
                    recipient,
                    NotificationType.RACE_CANCELLED,
                    "Race cancelled",
                    "Race " + raceName + " was cancelled. " + reason + ".",
                    RACE_REFERENCE,
                    String.valueOf(raceId),
                    metadata),
                    "race cancellation notification", raceId, recipient.getId());
            safeRun(() -> mailService.sendRaceCancelled(
                    recipient, raceName, reason, RACE_REFERENCE, String.valueOf(raceId)),
                    "race cancellation email", raceId, recipient.getId());
        }));
    }

    private void refundRegistrationFee(RaceRegistration registration, String note) {
        BigDecimal entryFee = registration.getEntryFeeAmount() == null
                ? BigDecimal.ZERO : registration.getEntryFeeAmount();
        if (entryFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String key = "race-registration:%d:entry-refund".formatted(registration.getId());
        walletService.debitAdmin(entryFee, WalletTransactionType.REFUND,
                REGISTRATION_REFERENCE, String.valueOf(registration.getId()),
                "race-registration:%d:entry-admin-refund".formatted(registration.getId()),
                null, note);
        walletService.refund(registration.getOwner().getId(), entryFee,
                REGISTRATION_REFERENCE, String.valueOf(registration.getId()), key, null, note);
        registration.setEntryFeeRefundKey(key);
    }

    private boolean isActive(RaceRegistration registration) {
        return registration.getStatus() == RaceRegistrationStatus.PENDING
                || registration.getStatus() == RaceRegistrationStatus.APPROVED;
    }

    private List<User> uniqueUsers(Collection<User> users) {
        Map<Long, User> unique = new LinkedHashMap<>();
        if (users != null) {
            users.forEach(user -> addUser(unique, user));
        }
        return List.copyOf(unique.values());
    }

    private void addUser(Map<Long, User> users, User user) {
        if (user != null && user.getId() != null) {
            users.putIfAbsent(user.getId(), user);
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void safeRun(Runnable action, String event, Long referenceId, Long recipientId) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Could not send {}: referenceId={}, recipientId={}",
                    event, referenceId, recipientId, ex);
        }
    }

    public record RaceCancellationResult(List<User> affectedUsers) {
    }

    private record RegistrationCancellationNotice(User owner, Long registrationId,
                                                  String raceName, String reason) {
    }
}
