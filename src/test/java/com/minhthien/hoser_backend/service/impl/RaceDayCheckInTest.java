package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceParticipantCheckInRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.JockeyChallengeResultRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceDayCheckInTest {
    @Mock private RaceRepository raceRepository;
    @Mock private RaceRegistrationRepository raceRegistrationRepository;
    @Mock private RaceParticipantRepository raceParticipantRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private RaceComplaintRepository raceComplaintRepository;
    @Mock private JockeyChallengeResultRepository jockeyChallengeResultRepository;
    @Mock private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private TournamentServiceImpl tournamentService;
    @Mock private FinanceSettingsService financeSettingsService;
    @Mock private MailService mailService;
    @Mock private BettingService bettingService;

    @InjectMocks
    private RaceDayServiceImpl service;

    @Test
    void checkInAfterDeadlineDoesNotCreateWalletTransactions() {
        User referee = user(1L, UserRole.REFEREE);
        User owner = user(2L, UserRole.OWNER);
        User jockey = user(3L, UserRole.JOCKEY);
        Tournament tournament = Tournament.builder()
                .id(5L)
                .name("Cup")
                .checkInDeadlineAt(LocalDateTime.now().minusMinutes(10))
                .build();
        Race race = Race.builder()
                .id(7L)
                .name("Race")
                .tournament(tournament)
                .referee(referee)
                .status(RaceStatus.SCHEDULED)
                .build();
        RaceParticipant participant = RaceParticipant.builder()
                .id(10L)
                .race(race)
                .registration(RaceRegistration.builder().id(8L).build())
                .owner(owner)
                .horse(Horse.builder().id(9L).name("Thunder").build())
                .jockey(jockey)
                .gateNumber(1)
                .status(RaceParticipantStatus.REGISTERED)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(referee));
        when(raceRepository.findById(7L)).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findById(10L)).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.save(participant)).thenReturn(participant);
        RaceParticipantCheckInRequest request = new RaceParticipantCheckInRequest();
        request.setStatus(RaceParticipantStatus.CHECKED_IN);

        var response = service.checkInRaceParticipant(1L, 7L, 10L, request);

        assertEquals(RaceParticipantStatus.CHECKED_IN, response.getStatus());
        verifyNoInteractions(walletService);
    }

    @Test
    void markingParticipantAbsentClearsAssignedGate() {
        User referee = user(1L, UserRole.REFEREE);
        User owner = user(2L, UserRole.OWNER);
        User jockey = user(3L, UserRole.JOCKEY);
        Race race = Race.builder()
                .id(7L)
                .name("Race")
                .tournament(Tournament.builder().id(5L).name("Cup").build())
                .referee(referee)
                .status(RaceStatus.SCHEDULED)
                .build();
        RaceParticipant participant = RaceParticipant.builder()
                .id(10L)
                .race(race)
                .registration(RaceRegistration.builder().id(8L).build())
                .owner(owner)
                .horse(Horse.builder().id(9L).name("Thunder").build())
                .jockey(jockey)
                .gateNumber(3)
                .status(RaceParticipantStatus.CHECKED_IN)
                .build();
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.save(participant)).thenReturn(participant);
        RaceParticipantCheckInRequest request = new RaceParticipantCheckInRequest();
        request.setStatus(RaceParticipantStatus.ABSENT);

        var response = service.checkInRaceParticipant(
                referee.getId(), race.getId(), participant.getId(), request);

        assertEquals(RaceParticipantStatus.ABSENT, response.getStatus());
        assertNull(response.getGateNumber());
    }

    private User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .username(role.name().toLowerCase())
                .email(role.name().toLowerCase() + "@example.com")
                .role(role)
                .active(true)
                .build();
    }
}
