package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationOpenBroadcastServiceImplTest {

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MailService mailService;

    @InjectMocks
    private RegistrationOpenBroadcastServiceImpl service;

    @Test
    void sendsRegistrationOpenEmailsInBatches() {
        Tournament tournament = tournament();
        List<User> firstBatch = LongStream.rangeClosed(1, 100)
                .mapToObj(this::user)
                .toList();
        List<User> secondBatch = List.of(user(101L));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));
        when(userRepository.findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                eq(true), eq(UserRole.ADMIN), eq(0L), any(Pageable.class))).thenReturn(firstBatch);
        when(userRepository.findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                eq(true), eq(UserRole.ADMIN), eq(100L), any(Pageable.class))).thenReturn(secondBatch);

        service.broadcastRegistrationOpen(3L);

        verify(mailService, times(101)).sendTournamentRegistrationOpen(eq(tournament), any(User.class));
        verify(userRepository).findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                eq(true), eq(UserRole.ADMIN), eq(0L), any(Pageable.class));
        verify(userRepository).findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                eq(true), eq(UserRole.ADMIN), eq(100L), any(Pageable.class));
    }

    @Test
    void continuesSendingWhenOneRecipientFails() {
        Tournament tournament = tournament();
        User first = user(1L);
        User second = user(2L);
        User third = user(3L);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));
        when(userRepository.findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                eq(true), eq(UserRole.ADMIN), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(first, second, third));
        doAnswer(invocation -> {
            User recipient = invocation.getArgument(1);
            if (recipient.getId().equals(2L)) {
                throw new RuntimeException("smtp failed");
            }
            return null;
        }).when(mailService).sendTournamentRegistrationOpen(eq(tournament), any(User.class));

        assertDoesNotThrow(() -> service.broadcastRegistrationOpen(3L));

        verify(mailService).sendTournamentRegistrationOpen(tournament, first);
        verify(mailService).sendTournamentRegistrationOpen(tournament, second);
        verify(mailService).sendTournamentRegistrationOpen(tournament, third);
    }

    @Test
    void exitsWhenTournamentIsMissing() {
        when(tournamentRepository.findById(3L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.broadcastRegistrationOpen(3L));

        verify(userRepository, never()).findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(
                any(), any(), any(), any(Pageable.class));
        verify(mailService, never()).sendTournamentRegistrationOpen(any(), any());
    }

    private Tournament tournament() {
        return Tournament.builder()
                .id(3L)
                .name("Summer Cup")
                .location("Ho Chi Minh City")
                .registrationOpenAt(LocalDateTime.now().minusDays(1))
                .registrationCloseAt(LocalDateTime.now().plusDays(1))
                .startAt(LocalDateTime.now().plusDays(5))
                .endAt(LocalDateTime.now().plusDays(6))
                .minTeams(1)
                .maxTeams(20)
                .status(TournamentStatus.OPEN_REGISTRATION)
                .build();
    }

    private User user(long id) {
        return User.builder()
                .id(id)
                .username("user" + id)
                .email("user" + id + "@example.com")
                .role(UserRole.USER)
                .active(true)
                .build();
    }
}
