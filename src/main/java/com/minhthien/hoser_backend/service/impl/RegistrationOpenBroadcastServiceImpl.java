package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationOpenBroadcastServiceImpl implements RegistrationOpenBroadcastService {
    private final UserRepository userRepository;
    private final MailService mailService;

    @Override
    public void broadcast(Tournament tournament) {
        userRepository.findByActiveAndRoleNotOrderByIdAsc(true, UserRole.ADMIN).forEach(recipient -> {
            try {
                mailService.sendTournamentRegistrationOpen(tournament, recipient);
            } catch (RuntimeException ex) {
                log.warn("Could not send registration-open email: tournamentId={}, recipientId={}",
                        tournament.getId(), recipient.getId(), ex);
            }
        });
    }
}
