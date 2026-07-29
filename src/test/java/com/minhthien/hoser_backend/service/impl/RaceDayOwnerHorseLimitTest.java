package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceRegistrationRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceDayOwnerHorseLimitTest {
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

    private User owner;
    private Race race;
    private JockeyInvitation invitation;

    @BeforeEach
    void setUp() {
        owner = user(2L, UserRole.OWNER);
        User jockey = user(3L, UserRole.JOCKEY);
        Tournament tournament = Tournament.builder()
                .id(5L)
                .name("Cup")
                .status(TournamentStatus.OPEN_REGISTRATION)
                .minHorsesPerOwner(4)
                .maxHorsesPerOwner(10)
                .build();
        race = Race.builder()
                .id(7L)
                .name("Race")
                .tournament(tournament)
                .status(RaceStatus.OPEN_REGISTRATION)
                .scheduledStartAt(LocalDateTime.of(2026, 7, 10, 10, 0))
                .scheduledEndAt(LocalDateTime.of(2026, 7, 10, 11, 0))
                .entryFee(BigDecimal.ZERO)
                .build();
        Horse horse = Horse.builder()
                .id(9L)
                .name("Thunder")
                .owner(owner)
                .status(HorseStatus.APPROVED)
                .build();
        JockeyProfile jockeyProfile = JockeyProfile.builder()
                .id(11L)
                .user(jockey)
                .status(JockeyStatus.APPROVED)
                .build();
        invitation = JockeyInvitation.builder()
                .id(12L)
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .jockeyProfile(jockeyProfile)
                .status(AssignmentStatus.ACCEPTED)
                .build();

        lenient().when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        lenient().when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        lenient().when(jockeyInvitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        lenient().when(raceRegistrationRepository.save(any(RaceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerCannotRegisterMoreThanOneHorseForSameRace() {
        when(raceRegistrationRepository.existsByRaceIdAndOwnerIdAndStatusIn(
                eq(race.getId()), eq(owner.getId()), anyCollection())).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.registerForRace(owner.getId(), race.getId(), request()));
    }

    @Test
    void ownerCannotRegisterMoreThanTournamentMaximum() {
        when(raceRegistrationRepository.countByRaceTournamentIdAndOwnerIdAndStatusIn(
                eq(race.getTournament().getId()), eq(owner.getId()), anyCollection())).thenReturn(10L);

        assertThrows(BadRequestException.class,
                () -> service.registerForRace(owner.getId(), race.getId(), request()));
    }

    @Test
    void ownerCanRegisterWhenBelowTournamentMaximum() {
        when(raceRegistrationRepository.countByRaceTournamentIdAndOwnerIdAndStatusIn(
                eq(race.getTournament().getId()), eq(owner.getId()), anyCollection())).thenReturn(9L);

        var response = service.registerForRace(owner.getId(), race.getId(), request());

        assertEquals(owner.getId(), response.getOwnerId());
    }

    @Test
    void scheduleRejectsOwnerBelowTournamentMinimum() {
        User admin = user(1L, UserRole.ADMIN);
        Tournament tournament = Tournament.builder()
                .id(5L)
                .name("Cup")
                .status(TournamentStatus.REGISTRATION_CLOSED)
                .minTeams(2)
                .maxTeams(20)
                .minHorsesPerOwner(4)
                .maxHorsesPerOwner(10)
                .build();
        Race scheduledRace = Race.builder()
                .id(7L)
                .name("Race")
                .tournament(tournament)
                .status(RaceStatus.REGISTRATION_CLOSED)
                .minParticipants(2)
                .maxParticipants(10)
                .build();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(tournament.getId())).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournament.getId()))
                .thenReturn(List.of(scheduledRace));
        when(raceParticipantRepository.countByRaceTournamentId(tournament.getId())).thenReturn(4L);
        when(raceRegistrationRepository.countByOwnerForTournament(
                eq(tournament.getId()), anyCollection())).thenReturn(List.<Object[]>of(new Object[]{2L, "owner", 3L}));

        assertThrows(BadRequestException.class,
                () -> service.scheduleTournament(admin.getId(), tournament.getId()));
    }

    private RaceRegistrationRequest request() {
        RaceRegistrationRequest request = new RaceRegistrationRequest();
        request.setHorseId(invitation.getHorse().getId());
        request.setJockeyInvitationId(invitation.getId());
        return request;
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
