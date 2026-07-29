package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentStatusScheduler {
    private static final String SYSTEM_USER = "SYSTEM";

    private final TournamentRepository tournamentRepository;
    private final RaceRepository raceRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RegistrationOpenBroadcastService registrationOpenBroadcastService;
    private final RaceCancellationService raceCancellationService;

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

            Map<Long, Long> participantCounts = activeRaces.stream()
                    .collect(Collectors.toMap(Race::getId,
                            race -> raceParticipantRepository.countByRaceId(race.getId())));
            Set<Long> ineligibleRaceIds = activeRaces.stream()
                    .filter(race -> participantCounts.getOrDefault(race.getId(), 0L)
                            < race.getMinParticipants())
                    .map(Race::getId)
                    .collect(Collectors.toSet());
            long approvedParticipantsInEligibleRaces = activeRaces.stream()
                    .filter(race -> !ineligibleRaceIds.contains(race.getId()))
                    .mapToLong(race -> participantCounts.getOrDefault(race.getId(), 0L))
                    .sum();

            if (approvedParticipantsInEligibleRaces < tournament.getMinTeams()) {
                Map<Long, User> affectedUsers = new LinkedHashMap<>();
                String reason = "Tournament auto-cancelled due to insufficient approved teams (%d/%d)"
                        .formatted(approvedParticipantsInEligibleRaces, tournament.getMinTeams());
                for (Race race : activeRaces) {
                    RaceCancellationService.RaceCancellationResult result =
                            raceCancellationService.cancelRace(
                                    race.getId(), null, reason, SYSTEM_USER, false);
                    if (result != null && result.affectedUsers() != null) {
                        result.affectedUsers().forEach(user -> {
                            if (user != null && user.getId() != null) {
                                affectedUsers.putIfAbsent(user.getId(), user);
                            }
                        });
                    }
                }
                tournament.setStatus(TournamentStatus.CANCELLED);
                tournament.setUpdatedBy(SYSTEM_USER);
                tournamentRepository.save(tournament);
                raceCancellationService.notifyTournamentCancelledAfterCommit(
                        tournament, affectedUsers.values(),
                        approvedParticipantsInEligibleRaces, tournament.getMinTeams());
                log.info("Tournament {} cancelled by scheduler - approved teams {}/{}",
                        tournament.getId(), approvedParticipantsInEligibleRaces, tournament.getMinTeams());
            } else {
                int cancelledCount = 0;
                for (Race race : activeRaces) {
                    if (ineligibleRaceIds.contains(race.getId())) {
                        String reason = "Race auto-cancelled due to insufficient approved participants (%d/%d)"
                                .formatted(participantCounts.getOrDefault(race.getId(), 0L),
                                        race.getMinParticipants());
                        raceCancellationService.cancelRace(
                                race.getId(), null, reason, SYSTEM_USER, true);
                        cancelledCount++;
                    }
                }
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
}
