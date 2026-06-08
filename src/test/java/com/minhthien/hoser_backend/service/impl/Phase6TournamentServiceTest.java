package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RacePrizeRequest;
import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RacePrize;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.BetMarketRepository;
import com.minhthien.hoser_backend.repository.BetRepository;
import com.minhthien.hoser_backend.repository.JockeyChallengeResultRepository;
import com.minhthien.hoser_backend.repository.RaceComplaintRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.TournamentRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import com.minhthien.hoser_backend.service.SystemSettingsService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase6TournamentServiceTest {
    private static final Long ADMIN_ID = 1L;

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private CloudinaryUploadService cloudinaryUploadService;
    @Mock
    private RaceParticipantRepository raceParticipantRepository;
    @Mock
    private RaceRepository raceRepository;
    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;
    @Mock
    private BetMarketRepository betMarketRepository;
    @Mock
    private BetRepository betRepository;
    @Mock
    private RaceResultRepository raceResultRepository;
    @Mock
    private RaceComplaintRepository raceComplaintRepository;
    @Mock
    private JockeyChallengeResultRepository jockeyChallengeResultRepository;
    @Mock
    private SystemSettingsService systemSettingsService;
    @Mock
    private RegistrationOpenBroadcastService registrationOpenBroadcastService;

    @InjectMocks
    private TournamentServiceImpl service;

    @BeforeEach
    void setUp() {
        User admin = User.builder()
                .id(ADMIN_ID)
                .username("admin")
                .role(UserRole.ADMIN)
                .build();

        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(raceParticipantRepository.countByRaceIds(anyCollection())).thenReturn(List.of());
        lenient().when(systemSettingsService.getCurrent()).thenReturn(SystemSettings.builder()
                .defaultRegistrationFee(new BigDecimal("5000000"))
                .lateCheckInFee(new BigDecimal("500000"))
                .build());
    }

    @Test
    void addTournamentRaceCreatesDraftRace() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, raceRequest("Qualifier"));

        assertEquals(RaceStatus.DRAFT, tournament.getRaces().get(0).getStatus());
        assertEquals(RaceStatus.DRAFT, response.getRaces().get(0).getStatus());
    }

    @Test
    void updateTournamentRaceKeepsDraftStatus() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race race = race(10L, tournament, RaceStatus.DRAFT, "Before");
        tournament.getRaces().add(race);
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        TournamentResponse response = service.updateTournamentRace(ADMIN_ID, 10L, raceRequest("After"));

        assertEquals(RaceStatus.DRAFT, race.getStatus());
        assertEquals(RaceStatus.DRAFT, response.getRaces().get(0).getStatus());
    }

    @Test
    void addRaceUsesDefaultFeeOnlyWhenEntryFeeIsOmitted() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Default fee race");
        request.setEntryFee(null);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(new BigDecimal("5000000"), tournament.getRaces().get(0).getEntryFee());
    }

    @Test
    void addRaceUsesDefaultLateCheckInFeeWhenOmitted() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Default late fee race");
        request.setLateCheckInFee(null);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(new BigDecimal("500000"), tournament.getRaces().get(0).getLateCheckInFee());
        assertEquals(new BigDecimal("500000"), response.getRaces().get(0).getLateCheckInFee());
    }

    @Test
    void addRaceUsesRequestLateCheckInFeeWhenProvided() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Override late fee race");
        request.setLateCheckInFee(new BigDecimal("125000"));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(new BigDecimal("125000"), tournament.getRaces().get(0).getLateCheckInFee());
        assertEquals(new BigDecimal("125000"), response.getRaces().get(0).getLateCheckInFee());
    }

    @Test
    void publishTournamentPublishesDraftRaces() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race draftRace = race(10L, tournament, RaceStatus.DRAFT, "Draft race");
        tournament.getRaces().add(draftRace);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.PUBLISHED);

        assertEquals(RaceStatus.PUBLISHED, draftRace.getStatus());
        assertEquals(RaceStatus.PUBLISHED, response.getRaces().get(0).getStatus());
    }

    @Test
    void registrationStatusesSyncToPreRaceStatuses() {
        Tournament tournament = tournament(TournamentStatus.PUBLISHED);
        Race race = race(10L, tournament, RaceStatus.PUBLISHED, "Race");
        tournament.getRaces().add(race);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse openResponse = service.updateTournamentStatus(
                ADMIN_ID, 3L, TournamentStatus.OPEN_REGISTRATION);
        TournamentResponse closedResponse = service.updateTournamentStatus(
                ADMIN_ID, 3L, TournamentStatus.REGISTRATION_CLOSED);

        assertEquals(RaceStatus.REGISTRATION_CLOSED, race.getStatus());
        assertEquals(RaceStatus.OPEN_REGISTRATION, openResponse.getRaces().get(0).getStatus());
        assertEquals(RaceStatus.REGISTRATION_CLOSED, closedResponse.getRaces().get(0).getStatus());
    }

    @Test
    void tournamentStatusSyncDoesNotChangeCancelledRace() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race draftRace = race(10L, tournament, RaceStatus.DRAFT, "Draft race");
        Race cancelledRace = race(11L, tournament, RaceStatus.CANCELLED, "Cancelled race");
        tournament.getRaces().addAll(List.of(draftRace, cancelledRace));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.PUBLISHED);

        assertEquals(RaceStatus.PUBLISHED, draftRace.getStatus());
        assertEquals(RaceStatus.CANCELLED, cancelledRace.getStatus());
        assertEquals(RaceStatus.PUBLISHED, response.getRaces().get(0).getStatus());
        assertEquals(RaceStatus.CANCELLED, response.getRaces().get(1).getStatus());
    }

    private Tournament tournament(TournamentStatus status) {
        return Tournament.builder()
                .id(3L)
                .name("Summer Cup")
                .location("Ho Chi Minh City")
                .registrationOpenAt(LocalDateTime.of(2026, 7, 1, 9, 0))
                .registrationCloseAt(LocalDateTime.of(2026, 7, 5, 17, 0))
                .startAt(LocalDateTime.of(2026, 7, 10, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 10, 18, 0))
                .minTeams(1)
                .maxTeams(20)
                .status(status)
                .jockeyChallengeEnabled(false)
                .build();
    }

    private Race race(Long id, Tournament tournament, RaceStatus status, String name) {
        Race race = Race.builder()
                .id(id)
                .tournament(tournament)
                .name(name)
                .distance("1000m")
                .scheduledStartAt(LocalDateTime.of(2026, 7, 10, 10, id.intValue() % 10))
                .scheduledEndAt(LocalDateTime.of(2026, 7, 10, 11, id.intValue() % 10))
                .minParticipants(1)
                .maxParticipants(8)
                .entryFee(BigDecimal.ZERO)
                .status(status)
                .build();
        race.replacePrizes(List.of(RacePrize.builder()
                .rank(1)
                .amount(BigDecimal.valueOf(100))
                .build()));
        return race;
    }

    private RaceRequest raceRequest(String name) {
        RaceRequest request = new RaceRequest();
        request.setName(name);
        request.setDistance("1000m");
        request.setScheduledStartAt(LocalDateTime.of(2026, 7, 10, 10, 0));
        request.setScheduledEndAt(LocalDateTime.of(2026, 7, 10, 11, 0));
        request.setMinParticipants(1);
        request.setMaxParticipants(8);
        request.setEntryFee(BigDecimal.ZERO);
        request.setLateCheckInFee(new BigDecimal("500000"));
        request.setPrizes(List.of(prizeRequest()));
        return request;
    }

    private RacePrizeRequest prizeRequest() {
        RacePrizeRequest request = new RacePrizeRequest();
        request.setRank(1);
        request.setAmount(BigDecimal.valueOf(100));
        return request;
    }
}
