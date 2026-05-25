package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceGateUpdateRequest;
import com.minhthien.hoser_backend.dto.request.RaceRefereeAssignmentRequest;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.JockeyChallengeResultRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase8RaceSchedulingServiceTest {
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceResultRepository raceResultRepository;
    @Mock
    private RaceComplaintRepository raceComplaintRepository;
    @Mock
    private JockeyChallengeResultRepository jockeyChallengeResultRepository;
    @Mock
    private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private TournamentServiceImpl tournamentService;
    @Mock
    private FinanceSettingsService financeSettingsService;
    @Mock
    private MailService mailService;

    @Test
    void scheduleTournamentPublishesScheduleAndEmailsRecipients() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User referee = user(8L, "referee", UserRole.REFEREE);
        Tournament tournament = tournament(TournamentStatus.REGISTRATION_CLOSED, 2, 4);
        Race race = race(10L, tournament, 2, referee, 0, 30);
        RaceParticipant first = participant(101L, race, 1, user(1L, "owner-1", UserRole.OWNER),
                user(2L, "jockey-1", UserRole.JOCKEY));
        RaceParticipant second = participant(102L, race, 2, user(3L, "owner-2", UserRole.OWNER),
                user(4L, "jockey-2", UserRole.JOCKEY));

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));
        when(raceParticipantRepository.countByRaceTournamentId(20L)).thenReturn(2L);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(first, second));
        when(tournamentRepository.save(tournament)).thenAnswer(invocation -> invocation.getArgument(0));
        when(tournamentService.mapToResponse(tournament))
                .thenReturn(TournamentResponse.builder().id(20L).status(TournamentStatus.SCHEDULED).build());

        var response = service.scheduleTournament(9L, 20L);

        assertThat(response.getStatus()).isEqualTo(TournamentStatus.SCHEDULED);
        assertThat(tournament.getStatus()).isEqualTo(TournamentStatus.SCHEDULED);
        assertThat(race.getStatus()).isEqualTo(RaceStatus.SCHEDULED);
        verify(mailService).sendRaceScheduled(race, referee);
        verify(mailService).sendRaceScheduled(race, first.getOwner());
        verify(mailService).sendRaceScheduled(race, first.getJockey());
        verify(mailService).sendRaceScheduled(race, second.getOwner());
        verify(mailService).sendRaceScheduled(race, second.getJockey());
    }

    @Test
    void scheduleTournamentRejectsWhenBelowMinTeams() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.OPEN_REGISTRATION, 3, 4);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L))
                .thenReturn(List.of(race(10L, tournament, 4, null, 0, 30)));
        when(raceParticipantRepository.countByRaceTournamentId(20L)).thenReturn(2L);

        assertThatThrownBy(() -> service.scheduleTournament(9L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tournament does not have enough approved participants");
    }

    @Test
    void scheduleTournamentRejectsDuplicateGate() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.REGISTRATION_CLOSED, 2, 4);
        Race race = race(10L, tournament, 4, null, 0, 30);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));
        when(raceParticipantRepository.countByRaceTournamentId(20L)).thenReturn(2L);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(
                        participant(101L, race, 1, user(1L, "owner-1", UserRole.OWNER),
                                user(2L, "jockey-1", UserRole.JOCKEY)),
                        participant(102L, race, 1, user(3L, "owner-2", UserRole.OWNER),
                                user(4L, "jockey-2", UserRole.JOCKEY))
                ));

        assertThatThrownBy(() -> service.scheduleTournament(9L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Gate number already exists in this race");
    }

    @Test
    void scheduleTournamentRejectsRaceOverCapacity() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.REGISTRATION_CLOSED, 2, 4);
        Race race = race(10L, tournament, 1, null, 0, 30);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));
        when(raceParticipantRepository.countByRaceTournamentId(20L)).thenReturn(2L);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(
                        participant(101L, race, 1, user(1L, "owner-1", UserRole.OWNER),
                                user(2L, "jockey-1", UserRole.JOCKEY)),
                        participant(102L, race, 2, user(3L, "owner-2", UserRole.OWNER),
                                user(4L, "jockey-2", UserRole.JOCKEY))
                ));

        assertThatThrownBy(() -> service.scheduleTournament(9L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race exceeds maximum participant capacity");
    }

    @Test
    void scheduleTournamentRejectsRefereeOverlap() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User referee = user(8L, "referee", UserRole.REFEREE);
        Tournament tournament = tournament(TournamentStatus.REGISTRATION_CLOSED, 1, 4);
        Race race = race(10L, tournament, 4, referee, 0, 30);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(20L)).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(20L)).thenReturn(List.of(race));
        when(raceParticipantRepository.countByRaceTournamentId(20L)).thenReturn(1L);
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(10L))
                .thenReturn(List.of(participant(101L, race, 1, user(1L, "owner", UserRole.OWNER),
                        user(2L, "jockey", UserRole.JOCKEY))));
        when(raceRepository.existsRefereeOverlapExcludingRace(8L, 10L,
                race.getScheduledStartAt(), race.getScheduledEndAt())).thenReturn(true);

        assertThatThrownBy(() -> service.scheduleTournament(9L, 20L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Referee cannot be assigned to overlapping races");
    }

    @Test
    void updateParticipantGateChangesGateBeforeResult() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.SCHEDULED, 1, 4);
        Race race = race(10L, tournament, 4, null, 0, 30);
        RaceParticipant participant = participant(101L, race, 1, user(1L, "owner", UserRole.OWNER),
                user(2L, "jockey", UserRole.JOCKEY));
        RaceGateUpdateRequest request = new RaceGateUpdateRequest();
        request.setGateNumber(3);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceParticipantRepository.findById(101L)).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.existsByRaceIdAndGateNumberAndIdNot(10L, 3, 101L)).thenReturn(false);
        when(raceParticipantRepository.save(participant)).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateParticipantGate(9L, 10L, 101L, request);

        assertThat(response.getGateNumber()).isEqualTo(3);
        assertThat(participant.getGateNumber()).isEqualTo(3);
    }

    @Test
    void updateParticipantGateRejectsDuplicateGate() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.SCHEDULED, 1, 4);
        Race race = race(10L, tournament, 4, null, 0, 30);
        RaceParticipant participant = participant(101L, race, 1, user(1L, "owner", UserRole.OWNER),
                user(2L, "jockey", UserRole.JOCKEY));
        RaceGateUpdateRequest request = new RaceGateUpdateRequest();
        request.setGateNumber(2);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceParticipantRepository.findById(101L)).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.existsByRaceIdAndGateNumberAndIdNot(10L, 2, 101L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateParticipantGate(9L, 10L, 101L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Gate number already exists in this race");
    }

    @Test
    void assignRaceRefereeRejectsNonRefereeUser() {
        RaceDayServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User notReferee = user(7L, "user", UserRole.USER);
        Tournament tournament = tournament(TournamentStatus.SCHEDULED, 1, 4);
        Race race = race(10L, tournament, 4, null, 0, 30);
        RaceRefereeAssignmentRequest request = new RaceRefereeAssignmentRequest();
        request.setRefereeId(7L);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));
        when(userRepository.findById(7L)).thenReturn(Optional.of(notReferee));

        assertThatThrownBy(() -> service.assignRaceReferee(9L, 10L, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Race referee must have REFEREE role");
    }

    private RaceDayServiceImpl service() {
        return new RaceDayServiceImpl(
                raceRepository,
                raceRegistrationRepository,
                raceParticipantRepository,
                raceResultRepository,
                raceComplaintRepository,
                jockeyChallengeResultRepository,
                jockeyInvitationRepository,
                tournamentRepository,
                userRepository,
                walletService,
                tournamentService,
                financeSettingsService,
                mailService
        );
    }

    private Tournament tournament(TournamentStatus status, int minTeams, int maxTeams) {
        return Tournament.builder()
                .id(20L)
                .name("Summer Race Day")
                .location("Ho Chi Minh City")
                .registrationOpenAt(LocalDateTime.of(2026, 6, 1, 9, 0))
                .registrationCloseAt(LocalDateTime.of(2026, 6, 10, 9, 0))
                .startAt(LocalDateTime.of(2026, 6, 16, 9, 0))
                .endAt(LocalDateTime.of(2026, 6, 16, 18, 0))
                .minTeams(minTeams)
                .maxTeams(maxTeams)
                .status(status)
                .build();
    }

    private Race race(Long id, Tournament tournament, int maxParticipants, User referee,
                      int startOffsetMinutes, int endOffsetMinutes) {
        return Race.builder()
                .id(id)
                .tournament(tournament)
                .name("Sprint")
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.of(2026, 6, 16, 9, 0).plusMinutes(startOffsetMinutes))
                .scheduledEndAt(LocalDateTime.of(2026, 6, 16, 9, 0).plusMinutes(endOffsetMinutes))
                .minParticipants(1)
                .maxParticipants(maxParticipants)
                .referee(referee)
                .status(RaceStatus.SCHEDULED)
                .build();
    }

    private RaceParticipant participant(Long id, Race race, int gateNumber, User owner, User jockey) {
        Horse horse = Horse.builder()
                .id(id + 1000)
                .name("Horse " + id)
                .owner(owner)
                .build();
        RaceRegistration registration = RaceRegistration.builder()
                .id(id + 2000)
                .race(race)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .build();
        return RaceParticipant.builder()
                .id(id)
                .race(race)
                .registration(registration)
                .owner(owner)
                .horse(horse)
                .jockey(jockey)
                .gateNumber(gateNumber)
                .build();
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .active(true)
                .build();
    }
}
