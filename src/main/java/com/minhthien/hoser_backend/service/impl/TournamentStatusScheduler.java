package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentStatusScheduler {
    private static final String SYSTEM_USER = "SYSTEM";

    private final TournamentRepository tournamentRepository;
    private final RegistrationOpenBroadcastService registrationOpenBroadcastService;

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
            tournament.setStatus(TournamentStatus.REGISTRATION_CLOSED);
            tournament.setUpdatedBy(SYSTEM_USER);
            TournamentStatusSync.syncPreRaceStatuses(tournament, TournamentStatus.REGISTRATION_CLOSED);
            tournamentRepository.save(tournament);
            updated++;
        }
        return updated;
    }
}
