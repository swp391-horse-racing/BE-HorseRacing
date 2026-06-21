package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationOpenBroadcastServiceImpl implements RegistrationOpenBroadcastService {
    private static final int BATCH_SIZE = 100;

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Override
    @Async("mailTaskExecutor")
    @Transactional(readOnly = true)
    public void broadcastRegistrationOpen(Long tournamentId) {
        if (tournamentId == null) {
            log.warn("Skipping registration-open email broadcast because tournamentId is null");
            return;
        }

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElse(null);
        if (tournament == null) {
            log.warn("Skipping registration-open email broadcast because tournament was not found: tournamentId={}",
                    tournamentId);
            return;
        }

        long lastSeenId = 0L;
        while (true) {
            List<User> recipients =
                    userRepository.findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                            true, UserRole.ADMIN, lastSeenId, PageRequest.of(0, BATCH_SIZE));
            if (recipients.isEmpty()) {
                break;
            }

            for (User recipient : recipients) {
                lastSeenId = recipient.getId();
                try {
                    mailService.sendTournamentRegistrationOpen(tournament, recipient);
                } catch (RuntimeException ex) {
                    log.warn("Could not send registration-open email: tournamentId={}, recipientId={}",
                            tournamentId, recipient.getId(), ex);
                }
            }

            if (recipients.size() < BATCH_SIZE) {
                break;
            }
        }
    }
}
