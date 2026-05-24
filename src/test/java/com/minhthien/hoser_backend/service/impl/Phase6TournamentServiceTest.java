package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.JockeyChallengePrizeRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeRequest;
import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.request.TournamentRequest;
import com.minhthien.hoser_backend.dto.request.TournamentUpdateRequest;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.JockeyChallengePrize;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RacePrize;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase6TournamentServiceTest {
    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @Test
    void adminCreatesDraftRaceDayWithChallengeConfigAndNoRequiredRaces() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> {
            Tournament tournament = invocation.getArgument(0);
            tournament.setId(10L);
            return tournament;
        });

        var response = service.createTournament(9L, request());

        assertThat(response.getStatus()).isEqualTo(TournamentStatus.DRAFT);
        assertThat(response.getRaces()).isEmpty();
        assertThat(response.getJockeyChallengeEnabled()).isTrue();
        assertThat(response.getJockeyChallengeFirstPoints()).isEqualTo(3);
        assertThat(response.getJockeyChallengePrizes()).hasSize(1);

        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("TOURNAMENT_CREATED");
        assertThat(auditCaptor.getValue().getReferenceId()).isEqualTo("10");
    }

    @Test
    void adminAddsRaceAfterCreatingRaceDay() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        tournament.replaceRaces(List.of());

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.addTournamentRace(9L, 10L, race("1000m Sprint", 0, 45));

        assertThat(response.getRaces()).hasSize(1);
        assertThat(response.getRaces().get(0).getName()).isEqualTo("1000m Sprint");

        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("TOURNAMENT_RACE_CREATED");
    }

    @Test
    void publishRejectsTournamentWithoutRaceConfig() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        tournament.replaceRaces(List.of());

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> service.updateTournamentStatus(9L, 10L, TournamentStatus.PUBLISHED))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tournament must have at least one race before publishing");
    }

    @Test
    void publishRejectsRaceWithoutPrizeConfig() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        tournament.getRaces().get(0).replacePrizes(List.of());

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> service.updateTournamentStatus(9L, 10L, TournamentStatus.PUBLISHED))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race must have at least one prize");
    }

    @Test
    void updateStatusMovesTournamentToPublishedAndOpenRegistration() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var published = service.updateTournamentStatus(9L, 10L, TournamentStatus.PUBLISHED);
        assertThat(published.getStatus()).isEqualTo(TournamentStatus.PUBLISHED);
        assertThat(tournament.getPublishedAt()).isNotNull();

        var opened = service.openRegistration(9L, 10L);
        assertThat(opened.getStatus()).isEqualTo(TournamentStatus.OPEN_REGISTRATION);
        assertThat(tournament.getOpenedRegistrationAt()).isNotNull();
    }

    @Test
    void updateTournamentKeepsExistingRaces() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setDescription("Updated description");

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateTournament(9L, 10L, request);

        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(response.getRaces()).hasSize(2);
    }

    @Test
    void updateTournamentReplacesChallengePrizesSafely() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        JockeyChallengePrizeRequest prize = new JockeyChallengePrizeRequest();
        prize.setRank(1);
        prize.setAmount(new BigDecimal("750000.00"));
        prize.setNote("Updated best jockey prize");
        request.setJockeyChallengePrizes(List.of(prize));

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateTournament(9L, 10L, request);

        assertThat(response.getJockeyChallengePrizes()).hasSize(1);
        assertThat(response.getJockeyChallengePrizes().get(0).getAmount()).isEqualByComparingTo("750000.00");
        verify(tournamentRepository).flush();
    }

    @Test
    void replaceTournamentRacesRejectsDuplicateNameAndStartTime() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> service.replaceTournamentRaces(9L, 10L,
                List.of(race("Sprint", 0, 45), race("Sprint", 0, 60))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race name and start time must be unique within a tournament");
    }

    @Test
    void replaceTournamentRacesRejectsScheduleOutsideTournamentWindow() {
        TournamentServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        Tournament tournament = tournament(TournamentStatus.DRAFT);

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> service.replaceTournamentRaces(9L, 10L,
                List.of(race("Late Sprint", 60 * 30, 60 * 31))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Race schedule must be within tournament time window");
    }

    @Test
    void publicTournamentRacesRejectDraftTournamentAsNotFound() {
        TournamentServiceImpl service = service();
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        when(tournamentRepository.findById(10L)).thenReturn(Optional.of(tournament));

        assertThatThrownBy(() -> service.getPublicTournamentRaces(10L))
                .isInstanceOf(com.minhthien.hoser_backend.exception.ResourceNotFoundException.class)
                .hasMessage("Tournament not found with id: '10'");
    }

    private TournamentServiceImpl service() {
        return new TournamentServiceImpl(tournamentRepository, userRepository, adminAuditLogRepository);
    }

    private TournamentRequest request() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 1, 9, 0);
        TournamentRequest request = new TournamentRequest();
        request.setName("Summer Race Day");
        request.setDescription("Race day tournament");
        request.setLocation("Ho Chi Minh City");
        request.setRegistrationOpenAt(base);
        request.setRegistrationCloseAt(base.plusDays(10));
        request.setStartAt(base.plusDays(15));
        request.setEndAt(base.plusDays(16));
        request.setCheckInDeadlineAt(base.plusDays(15).minusHours(2));
        request.setMinTeams(4);
        request.setMaxTeams(16);
        request.setJockeyChallengeEnabled(true);
        request.setJockeyChallengeFirstPoints(3);
        request.setJockeyChallengeSecondPoints(2);
        request.setJockeyChallengeThirdPoints(1);
        JockeyChallengePrizeRequest challengePrize = new JockeyChallengePrizeRequest();
        challengePrize.setRank(1);
        challengePrize.setAmount(new BigDecimal("500000.00"));
        request.setJockeyChallengePrizes(List.of(challengePrize));
        return request;
    }

    private RaceRequest race(String name, int startOffsetMinutes, int endOffsetMinutes) {
        LocalDateTime start = LocalDateTime.of(2026, 6, 16, 9, 0).plusMinutes(startOffsetMinutes);
        RaceRequest request = new RaceRequest();
        request.setName(name);
        request.setDistance(name.split(" ")[0]);
        request.setScheduledStartAt(start);
        request.setScheduledEndAt(LocalDateTime.of(2026, 6, 16, 9, 0).plusMinutes(endOffsetMinutes));
        request.setMinParticipants(2);
        request.setMaxParticipants(8);
        request.setEntryFee(new BigDecimal("10000.00"));
        RacePrizeRequest first = new RacePrizeRequest();
        first.setRank(1);
        first.setAmount(new BigDecimal("1000000.00"));
        RacePrizeRequest second = new RacePrizeRequest();
        second.setRank(2);
        second.setAmount(new BigDecimal("500000.00"));
        request.setPrizes(List.of(first, second));
        return request;
    }

    private Tournament tournament(TournamentStatus status) {
        Tournament tournament = Tournament.builder()
                .id(10L)
                .name("Summer Race Day")
                .location("Ho Chi Minh City")
                .registrationOpenAt(LocalDateTime.of(2026, 6, 1, 9, 0))
                .registrationCloseAt(LocalDateTime.of(2026, 6, 10, 9, 0))
                .startAt(LocalDateTime.of(2026, 6, 16, 9, 0))
                .endAt(LocalDateTime.of(2026, 6, 17, 9, 0))
                .entryFee(new BigDecimal("100000.00"))
                .minTeams(4)
                .maxTeams(16)
                .jockeyChallengeEnabled(true)
                .jockeyChallengeFirstPoints(3)
                .jockeyChallengeSecondPoints(2)
                .jockeyChallengeThirdPoints(1)
                .status(status)
                .build();
        tournament.replaceRaces(List.of(
                raceEntity("1000m Sprint", 0, 45),
                raceEntity("1600m Classic", 90, 150)
        ));
        tournament.replaceJockeyChallengePrizes(List.of(
                JockeyChallengePrize.builder()
                        .rank(1)
                        .amount(new BigDecimal("500000.00"))
                        .build()
        ));
        return tournament;
    }

    private Race raceEntity(String name, int startOffsetMinutes, int endOffsetMinutes) {
        Race race = Race.builder()
                .name(name)
                .distance(name.split(" ")[0])
                .scheduledStartAt(LocalDateTime.of(2026, 6, 16, 9, 0).plusMinutes(startOffsetMinutes))
                .scheduledEndAt(LocalDateTime.of(2026, 6, 16, 9, 0).plusMinutes(endOffsetMinutes))
                .minParticipants(2)
                .maxParticipants(8)
                .entryFee(new BigDecimal("10000.00"))
                .build();
        race.replacePrizes(List.of(
                RacePrize.builder().rank(1).amount(new BigDecimal("1000000.00")).build(),
                RacePrize.builder().rank(2).amount(new BigDecimal("500000.00")).build()
        ));
        return race;
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .build();
    }
}
