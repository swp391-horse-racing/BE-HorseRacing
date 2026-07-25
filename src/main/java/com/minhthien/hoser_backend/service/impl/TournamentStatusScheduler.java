package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RefereeInvitationService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentStatusScheduler {
    private static final String SYSTEM_USER = "SYSTEM";

    private final TournamentRepository tournamentRepository;
    private final RaceRepository raceRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final RegistrationOpenBroadcastService registrationOpenBroadcastService;
    private final WalletService walletService;
    private final BettingService bettingService;
    private final RefereeInvitationService refereeInvitationService;
    private final RefereePaymentService refereePaymentService;

    private NotificationService notificationService;

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(
            initialDelayString = "${app.tournament-status.initial-delay-ms:30000}",
            fixedDelayString = "${app.tournament-status.delay-ms:60000}"
    )
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public void updateRegistrationStatuses() {
        LocalDateTime now = LocalDateTime.now();
        int opened = openDueRegistrations(now);
        int closed = closeDueRegistrations(now);
        if (opened > 0 || closed > 0) {
            log.info("Updated tournament registration statuses: opened={}, closed={}", opened, closed);
        }
    }

    private int openDueRegistrations(LocalDateTime now) {
        int updated = 0;
        for (Tournament tournament : tournamentRepository
                .findByStatusAndRegistrationOpenAtLessThanEqualOrderByRegistrationOpenAtAsc(
                        TournamentStatus.PUBLISHED, now)) {
            tournament.setStatus(TournamentStatus.OPEN_REGISTRATION);
            if (tournament.getOpenedRegistrationAt() == null) {
                tournament.setOpenedRegistrationAt(now);
            }
            tournament.setUpdatedBy(SYSTEM_USER);
            TournamentStatusSync.syncPreRaceStatuses(tournament, TournamentStatus.OPEN_REGISTRATION);
            tournamentRepository.save(tournament);
            scheduleRegistrationOpenBroadcast(tournament.getId());
            updated++;
        }
        return updated;
    }

    private void scheduleRegistrationOpenBroadcast(Long tournamentId) {
        Runnable broadcast = () -> {
            try {
                registrationOpenBroadcastService.broadcastRegistrationOpen(tournamentId);
            } catch (RuntimeException ex) {
                log.warn("Could not schedule registration-open email broadcast: tournamentId={}", tournamentId, ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast.run();
                }
            });
            return;
        }

        broadcast.run();
    }

    private int closeDueRegistrations(LocalDateTime now) {
        int updated = 0;
        for (Tournament tournament : tournamentRepository
                .findByStatusAndRegistrationCloseAtLessThanEqualOrderByRegistrationCloseAtAsc(
                        TournamentStatus.OPEN_REGISTRATION, now)) {

            List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournament.getId());
            List<Race> activeRaces = races.stream()
                    .filter(r -> r.getStatus() != RaceStatus.CANCELLED)
                    .toList();

            int cancelledCount = 0;
            for (Race race : activeRaces) {
                long participantCount = raceParticipantRepository.countByRaceId(race.getId());
                if (participantCount < race.getMinParticipants()) {
                    cancelIneligibleRace(race);
                    cancelledCount++;
                }
            }

            long remainingActiveRaces = activeRaces.stream()
                    .filter(r -> r.getStatus() != RaceStatus.CANCELLED)
                    .count();

            if (remainingActiveRaces == 0) {
                tournament.setStatus(TournamentStatus.CANCELLED);
                tournament.setUpdatedBy(SYSTEM_USER);
                tournamentRepository.save(tournament);
                log.info("Tournament {} cancelled by scheduler - all races ineligible", tournament.getId());
            } else {
                tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
                tournament.setUpdatedBy(SYSTEM_USER);
                TournamentStatusSync.syncPreRaceStatuses(tournament, TournamentStatus.REGISTRATION_CLOSED);
                tournamentRepository.save(tournament);
                if (cancelledCount > 0) {
                    log.info("Tournament {} registration closed - {} races cancelled due to insufficient participants",
                            tournament.getId(), cancelledCount);
                }
            }
            updated++;
        }
        return updated;
    }

    private void cancelIneligibleRace(Race race) {
        String cancelNote = "Race auto-cancelled by system due to insufficient participants (required: "
                + race.getMinParticipants() + ")";

        raceRegistrationRepository.findByRaceIdOrderByCreatedAtDesc(race.getId()).stream()
                .filter(reg -> reg.getStatus() == RaceRegistrationStatus.PENDING
                        || reg.getStatus() == RaceRegistrationStatus.APPROVED)
                .forEach(registration -> {
                    refundRegistrationFee(registration,
                            "Race entry fee refunded - race auto-cancelled due to insufficient participants");
                    registration.setStatus(RaceRegistrationStatus.CANCELLED);
                    registration.setReviewedAt(LocalDateTime.now());
                    registration.setReviewNote(cancelNote);
                    raceRegistrationRepository.save(registration);
                    if (notificationService != null) {
                        try {
                            notificationService.notify(
                                    registration.getOwner(),
                                    NotificationType.REGISTRATION_CANCELLED,
                                    "Race registration cancelled",
                                    "Your registration for race " + registration.getRace().getName()
                                            + " was auto-cancelled due to insufficient participants. "
                                            + "Your entry fee has been refunded.",
                                    "RACE_REGISTRATION",
                                    String.valueOf(registration.getId()),
                                    null);
                        } catch (RuntimeException ex) {
                            log.warn("Failed to send auto-cancellation notification for registration {}",
                                    registration.getId(), ex);
                        }
                    }
                });

        bettingService.cancelRaceBets(race.getId());
        refereeInvitationService.cancelPendingInvitationsForRace(race.getId(), cancelNote);
        refereePaymentService.releaseForCancelledRace(null, race);
        race.setStatus(RaceStatus.CANCELLED);
        raceRepository.save(race);
        log.info("Scheduler cancelled ineligible race: id={}, name={}", race.getId(), race.getName());
    }

    private void refundRegistrationFee(RaceRegistration registration, String note) {
        BigDecimal entryFee = registration.getEntryFeeAmount() == null
                ? BigDecimal.ZERO : registration.getEntryFeeAmount();
        if (entryFee.compareTo(BigDecimal.ZERO) > 0) {
            String key = "race-registration:%d:entry-refund".formatted(registration.getId());
            walletService.debitAdmin(entryFee, WalletTransactionType.REFUND,
                    "RACE_REGISTRATION", String.valueOf(registration.getId()),
                    "race-registration:%d:entry-admin-refund".formatted(registration.getId()),
                    null, note);
            walletService.refund(registration.getOwner().getId(), entryFee,
                    "RACE_REGISTRATION", String.valueOf(registration.getId()), key, null, note);
            registration.setEntryFeeRefundKey(key);
        }
    }
}
