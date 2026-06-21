package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RaceGateUpdateRequest;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceRegistration;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
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
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceDayGateAssignmentTest {
    @Mock private RaceRepository raceRepository;
    @Mock private RaceRegistrationRepository raceRegistrationRepository;
    @Mock private RaceParticipantRepository raceParticipantRepository;
    @Mock private com.minhthien.hoser_backend.repository.RaceResultRepository raceResultRepository;
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
    void approveRegistrationCreatesParticipantWithoutGate() {
        User admin = user(1L, UserRole.ADMIN);
        RaceRegistration registration = registration();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(raceRegistrationRepository.findById(registration.getId())).thenReturn(Optional.of(registration));
        when(raceRegistrationRepository.save(registration)).thenReturn(registration);

        service.approveRaceRegistration(admin.getId(), registration.getId(), null);

        ArgumentCaptor<RaceParticipant> participantCaptor = ArgumentCaptor.forClass(RaceParticipant.class);
        verify(raceParticipantRepository).save(participantCaptor.capture());
        assertNull(participantCaptor.getValue().getGateNumber());
        verify(raceParticipantRepository, never()).existsByRaceIdAndGateNumber(any(), any());
    }

    @Test
    void assignedRefereeCanUpdateParticipantGate() {
        User referee = user(1L, UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(race, null);
        RaceGateUpdateRequest request = gateRequest(4);
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceResultRepository.existsByRaceId(race.getId())).thenReturn(false);
        when(raceParticipantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.existsByRaceIdAndGateNumberAndIdNot(race.getId(), 4, participant.getId()))
                .thenReturn(false);
        when(raceParticipantRepository.save(participant)).thenReturn(participant);

        var response = service.updateRefereeParticipantGate(referee.getId(), race.getId(), participant.getId(), request);

        assertEquals(4, response.getGateNumber());
    }

    @Test
    void unassignedRefereeCannotUpdateParticipantGate() {
        User assignedReferee = user(1L, UserRole.REFEREE);
        User otherReferee = user(2L, UserRole.REFEREE);
        Race race = race(assignedReferee, RaceStatus.SCHEDULED);
        when(userRepository.findById(otherReferee.getId())).thenReturn(Optional.of(otherReferee));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));

        assertThrows(UnauthorizedException.class,
                () -> service.updateRefereeParticipantGate(otherReferee.getId(), race.getId(), 10L, gateRequest(2)));
    }

    @Test
    void getTodayRefereeRacesUsesCurrentDayRange() {
        User referee = user(1L, UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);
        RaceResponse mapped = RaceResponse.builder().id(race.getId()).name(race.getName()).build();
        LocalDate today = LocalDate.now();
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findByRefereeIdAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
                eq(referee.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(race));
        when(tournamentService.mapRace(race)).thenReturn(mapped);

        var responses = service.getTodayRefereeRaces(referee.getId());

        assertEquals(1, responses.size());
        assertEquals(race.getId(), responses.get(0).getId());
        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(raceRepository).findByRefereeIdAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
                eq(referee.getId()), startCaptor.capture(), endCaptor.capture());
        assertEquals(today.atStartOfDay(), startCaptor.getValue());
        assertEquals(today.plusDays(1).atStartOfDay(), endCaptor.getValue());
    }

    @Test
    void getTodayRefereeRacesReturnsEmptyListWhenNoAssignedRaceToday() {
        User referee = user(1L, UserRole.REFEREE);
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findByRefereeIdAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
                eq(referee.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        var responses = service.getTodayRefereeRaces(referee.getId());

        assertEquals(0, responses.size());
    }

    @Test
    void getTodayRefereeRacesRejectsNonReferee() {
        User owner = user(3L, UserRole.OWNER);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));

        assertThrows(UnauthorizedException.class, () -> service.getTodayRefereeRaces(owner.getId()));
    }

    @Test
    void duplicateGateIsRejected() {
        User referee = user(1L, UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(race, null);
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceResultRepository.existsByRaceId(race.getId())).thenReturn(false);
        when(raceParticipantRepository.findById(participant.getId())).thenReturn(Optional.of(participant));
        when(raceParticipantRepository.existsByRaceIdAndGateNumberAndIdNot(race.getId(), 3, participant.getId()))
                .thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.updateRefereeParticipantGate(referee.getId(), race.getId(), participant.getId(), gateRequest(3)));
    }

    @Test
    void gateCannotBeUpdatedAfterRaceStarts() {
        User referee = user(1L, UserRole.REFEREE);
        Race race = race(referee, RaceStatus.ONGOING);
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));

        assertThrows(BadRequestException.class,
                () -> service.updateRefereeParticipantGate(referee.getId(), race.getId(), 10L, gateRequest(1)));
    }

    @Test
    void scheduleTournamentDoesNotRequireAssignedGates() {
        User admin = user(1L, UserRole.ADMIN);
        User referee = user(2L, UserRole.REFEREE);
        Tournament tournament = tournament(TournamentStatus.REGISTRATION_CLOSED);
        Race race = race(referee, RaceStatus.REGISTRATION_CLOSED);
        race.setTournament(tournament);
        RaceParticipant participant = participant(race, null);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(tournament.getId())).thenReturn(Optional.of(tournament));
        when(raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournament.getId())).thenReturn(List.of(race));
        when(raceParticipantRepository.countByRaceTournamentId(tournament.getId())).thenReturn(1L);
        when(raceRegistrationRepository.countByOwnerForTournament(eq(tournament.getId()), anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, "owner", 1L}));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId())).thenReturn(List.of(participant));
        when(tournamentRepository.save(tournament)).thenReturn(tournament);
        when(tournamentService.mapToResponse(tournament)).thenReturn(TournamentResponse.builder().id(tournament.getId()).build());

        service.scheduleTournament(admin.getId(), tournament.getId());

        assertEquals(TournamentStatus.SCHEDULED, tournament.getStatus());
        assertEquals(RaceStatus.SCHEDULED, race.getStatus());
    }

    @Test
    void startRaceRequiresAssignedGates() {
        User referee = user(1L, UserRole.REFEREE);
        Race race = race(referee, RaceStatus.SCHEDULED);
        RaceParticipant participant = participant(race, null);
        participant.setStatus(RaceParticipantStatus.CHECKED_IN);
        when(userRepository.findById(referee.getId())).thenReturn(Optional.of(referee));
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId())).thenReturn(List.of(participant));

        assertThrows(BadRequestException.class, () -> service.startRace(referee.getId(), race.getId()));
    }

    private RaceRegistration registration() {
        User owner = user(3L, UserRole.OWNER);
        User jockey = user(4L, UserRole.JOCKEY);
        Tournament tournament = tournament(TournamentStatus.OPEN_REGISTRATION);
        Race race = race(user(2L, UserRole.REFEREE), RaceStatus.OPEN_REGISTRATION);
        race.setTournament(tournament);
        return RaceRegistration.builder()
                .id(20L)
                .race(race)
                .owner(owner)
                .horse(Horse.builder().id(30L).name("Thunder").build())
                .jockey(jockey)
                .jockeyInvitation(JockeyInvitation.builder().id(40L).build())
                .status(RaceRegistrationStatus.PENDING)
                .entryFeeAmount(BigDecimal.ZERO)
                .build();
    }

    private RaceParticipant participant(Race race, Integer gateNumber) {
        User owner = user(3L, UserRole.OWNER);
        User jockey = user(4L, UserRole.JOCKEY);
        return RaceParticipant.builder()
                .id(10L)
                .race(race)
                .registration(RaceRegistration.builder().id(20L).build())
                .owner(owner)
                .horse(Horse.builder().id(30L).name("Thunder").build())
                .jockey(jockey)
                .gateNumber(gateNumber)
                .status(RaceParticipantStatus.REGISTERED)
                .build();
    }

    private Race race(User referee, RaceStatus status) {
        return Race.builder()
                .id(7L)
                .name("Race")
                .distance("1000m")
                .tournament(tournament(TournamentStatus.SCHEDULED))
                .scheduledStartAt(LocalDateTime.now().plusHours(1))
                .scheduledEndAt(LocalDateTime.now().plusHours(2))
                .minParticipants(1)
                .maxParticipants(10)
                .referee(referee)
                .status(status)
                .build();
    }

    private Tournament tournament(TournamentStatus status) {
        return Tournament.builder()
                .id(5L)
                .name("Cup")
                .minTeams(1)
                .maxTeams(10)
                .minHorsesPerOwner(1)
                .maxHorsesPerOwner(10)
                .status(status)
                .build();
    }

    private RaceGateUpdateRequest gateRequest(Integer gateNumber) {
        RaceGateUpdateRequest request = new RaceGateUpdateRequest();
        request.setGateNumber(gateNumber);
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
