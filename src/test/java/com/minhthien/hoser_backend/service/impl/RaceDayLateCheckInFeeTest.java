package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceParticipantCheckInRequest;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceDayLateCheckInFeeTest {
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
    void lateCheckInChargesOwnerAndAdminExactlyOnce() {
        User referee = user(1L, UserRole.REFEREE);
        User owner = user(2L, UserRole.OWNER);
        User jockey = user(3L, UserRole.JOCKEY);
        Tournament tournament = Tournament.builder()
                .id(5L)
                .name("Cup")
                .checkInDeadlineAt(LocalDateTime.now().minusMinutes(10))
                .lateCheckInFee(new BigDecimal("500000"))
                .build();
        Race race = Race.builder()
                .id(7L)
                .name("Race")
                .tournament(tournament)
                .referee(referee)
                .status(RaceStatus.SCHEDULED)
                .build();
        RaceRegistration registration = RaceRegistration.builder().id(8L).build();
        Horse horse = Horse.builder().id(9L).name("Thunder").build();
        RaceParticipant participant = RaceParticipant.builder()
                .id(10L)
                .race(race)
                .registration(registration)
                .owner(owner)
                .horse(horse)
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

        var first = service.checkInRaceParticipant(1L, 7L, 10L, request);
        var second = service.checkInRaceParticipant(1L, 7L, 10L, request);

        assertEquals(new BigDecimal("500000"), first.getLateCheckInFeeAmount());
        assertTrue(first.getLateCheckInFeeCharged());
        assertTrue(second.getLateCheckInFeeCharged());
        verify(walletService, times(1)).debitAllowNegative(
                eq(2L), eq(new BigDecimal("500000")), eq(WalletTransactionType.LATE_CHECK_IN_FEE),
                eq("RACE_PARTICIPANT"), eq("10"), anyString(), anyString(), anyString());
        verify(walletService, times(1)).creditAdmin(
                eq(new BigDecimal("500000")), eq(WalletTransactionType.LATE_CHECK_IN_FEE),
                eq("RACE_PARTICIPANT"), eq("10"), anyString(), anyString(), anyString());
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
