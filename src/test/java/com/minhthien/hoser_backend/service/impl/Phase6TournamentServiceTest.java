package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RacePrizeRequest;
import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.request.JockeyChallengePrizeRequest;
import com.minhthien.hoser_backend.dto.request.TournamentRequest;
import com.minhthien.hoser_backend.dto.request.TournamentUpdateRequest;
import com.minhthien.hoser_backend.dto.response.RaceVenueResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.entity.Province;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RacePrize;
import com.minhthien.hoser_backend.entity.RaceVenue;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
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
import com.minhthien.hoser_backend.service.LocationSettingsService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Phase6TournamentServiceTest {
    private static final Long ADMIN_ID = 1L;
    private static final Long PROVINCE_ID = 50L;
    private static final Long VENUE_ID = 60L;
    private static final Long OTHER_VENUE_ID = 61L;

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
    @Mock
    private LocationSettingsService locationSettingsService;

    @InjectMocks
    private TournamentServiceImpl service;

    @BeforeEach
    void setUp() {
        User admin = User.builder()
                .id(ADMIN_ID)
                .username("admin")
                .role(UserRole.ADMIN)
                .build();

        lenient().when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        lenient().when(tournamentRepository.save(any(Tournament.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(raceParticipantRepository.countByRaceIds(anyCollection())).thenReturn(List.of());
        lenient().when(raceRegistrationRepository.countByOwnerForTournament(any(), anyCollection())).thenReturn(List.of());
        lenient().when(systemSettingsService.getCurrent()).thenReturn(SystemSettings.builder()
                .defaultRegistrationFee(new BigDecimal("5000000"))
                .lateCheckInFee(new BigDecimal("500000"))
                .build());
        lenient().when(systemSettingsService.normalizeRaceDistance(anyString()))
                .thenAnswer(invocation -> normalizeRaceDistance(invocation.getArgument(0)));
        lenient().when(locationSettingsService.requireActiveProvince(PROVINCE_ID)).thenReturn(province());
        lenient().when(locationSettingsService.requireActiveVenue(VENUE_ID)).thenReturn(venue());
        lenient().when(locationSettingsService.requireActiveVenue(OTHER_VENUE_ID)).thenReturn(otherProvinceVenue());
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
    void addRaceRejectsMinParticipantsAboveMaxParticipants() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Invalid participants");
        request.setMinParticipants(9);
        request.setMaxParticipants(8);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.addTournamentRace(ADMIN_ID, 3L, request));

        assertEquals("Race minimum participants must not exceed maximum participants", exception.getMessage());
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
    void addRaceRejectsZeroLateCheckInFee() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Zero late fee race");
        request.setLateCheckInFee(BigDecimal.ZERO);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.addTournamentRace(ADMIN_ID, 3L, request));

        assertEquals("Race late check-in fee must be greater than zero", exception.getMessage());
    }

    @Test
    void addRaceRejectsNegativeLateCheckInFee() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Negative late fee race");
        request.setLateCheckInFee(new BigDecimal("-1"));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.addTournamentRace(ADMIN_ID, 3L, request));

        assertEquals("Race late check-in fee must be greater than zero", exception.getMessage());
    }

    @Test
    void updateRaceRejectsZeroLateCheckInFee() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race race = race(10L, tournament, RaceStatus.DRAFT, "Before");
        RaceRequest request = raceRequest("After");
        request.setLateCheckInFee(BigDecimal.ZERO);
        tournament.getRaces().add(race);
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.updateTournamentRace(ADMIN_ID, 10L, request));

        assertEquals("Race late check-in fee must be greater than zero", exception.getMessage());
    }

    @Test
    void addRaceNormalizesConfiguredDistanceWithoutUnit() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Normalized distance");
        request.setDistance("1000");
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals("1000m", tournament.getRaces().get(0).getDistance());
        assertEquals("1000m", response.getRaces().get(0).getDistance());
    }

    @Test
    void addRaceRejectsUnconfiguredDistance() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Bad distance");
        request.setDistance("1300m");
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        assertThrows(BadRequestException.class, () -> service.addTournamentRace(ADMIN_ID, 3L, request));
    }

    @Test
    void updateRaceNormalizesConfiguredDistanceWithoutUnit() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race race = race(10L, tournament, RaceStatus.DRAFT, "Before");
        RaceRequest request = raceRequest("After");
        request.setDistance("1200");
        tournament.getRaces().add(race);
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        TournamentResponse response = service.updateTournamentRace(ADMIN_ID, 10L, request);

        assertEquals("1200m", race.getDistance());
        assertEquals("1200m", response.getRaces().get(0).getDistance());
    }

    @Test
    void addRaceRejectsVenueFromAnotherProvince() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Wrong province venue");
        request.setVenueId(OTHER_VENUE_ID);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        assertThrows(BadRequestException.class, () -> service.addTournamentRace(ADMIN_ID, 3L, request));
    }

    @Test
    void tournamentVenueOptionsDelegatesToLocationSettings() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceVenueResponse venueResponse = RaceVenueResponse.builder()
                .id(VENUE_ID)
                .provinceId(PROVINCE_ID)
                .name("Phu Tho Racecourse")
                .build();
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));
        when(locationSettingsService.getActiveVenuesByTournament(3L)).thenReturn(List.of(venueResponse));

        List<RaceVenueResponse> response = service.getTournamentVenueOptions(3L);

        assertEquals(List.of(VENUE_ID), response.stream().map(RaceVenueResponse::getId).toList());
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

    @Test
    void statusTransitionAllowsDraftToCancelled() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.CANCELLED);

        assertEquals(TournamentStatus.CANCELLED, tournament.getStatus());
        assertEquals(TournamentStatus.CANCELLED, response.getStatus());
    }

    @Test
    void statusTransitionAllowsPublishedToCancelled() {
        Tournament tournament = tournament(TournamentStatus.PUBLISHED);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.CANCELLED);

        assertEquals(TournamentStatus.CANCELLED, tournament.getStatus());
        assertEquals(TournamentStatus.CANCELLED, response.getStatus());
    }

    @Test
    void statusTransitionAllowsRegistrationClosedToScheduled() {
        Tournament tournament = tournament(TournamentStatus.REGISTRATION_CLOSED);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.SCHEDULED);

        assertEquals(TournamentStatus.SCHEDULED, tournament.getStatus());
        assertEquals(TournamentStatus.SCHEDULED, response.getStatus());
    }

    @Test
    void statusTransitionAllowsScheduledToOngoing() {
        Tournament tournament = tournament(TournamentStatus.SCHEDULED);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.ONGOING);

        assertEquals(TournamentStatus.ONGOING, tournament.getStatus());
        assertEquals(TournamentStatus.ONGOING, response.getStatus());
    }

    @Test
    void statusTransitionRejectsDraftToOpenRegistration() {
        assertInvalidStatusTransition(TournamentStatus.DRAFT, TournamentStatus.OPEN_REGISTRATION);
    }

    @Test
    void statusTransitionRejectsPublishedToRegistrationClosed() {
        assertInvalidStatusTransition(TournamentStatus.PUBLISHED, TournamentStatus.REGISTRATION_CLOSED);
    }

    @Test
    void statusTransitionRejectsOpenRegistrationToScheduled() {
        assertInvalidStatusTransition(TournamentStatus.OPEN_REGISTRATION, TournamentStatus.SCHEDULED);
    }

    @Test
    void statusTransitionRejectsOngoingToCompletedViaStatusEndpoint() {
        assertInvalidStatusTransition(TournamentStatus.ONGOING, TournamentStatus.COMPLETED);
    }

    @Test
    void statusTransitionRejectsCancelledToPublicStatuses() {
        for (TournamentStatus targetStatus : List.of(
                TournamentStatus.PUBLISHED,
                TournamentStatus.OPEN_REGISTRATION,
                TournamentStatus.REGISTRATION_CLOSED,
                TournamentStatus.SCHEDULED,
                TournamentStatus.ONGOING,
                TournamentStatus.COMPLETED)) {
            assertInvalidStatusTransition(TournamentStatus.CANCELLED, targetStatus);
        }
    }

    @Test
    void statusTransitionRejectsCompletedToOtherStatuses() {
        for (TournamentStatus targetStatus : List.of(
                TournamentStatus.DRAFT,
                TournamentStatus.PUBLISHED,
                TournamentStatus.OPEN_REGISTRATION,
                TournamentStatus.REGISTRATION_CLOSED,
                TournamentStatus.SCHEDULED,
                TournamentStatus.ONGOING,
                TournamentStatus.CANCELLED)) {
            assertInvalidStatusTransition(TournamentStatus.COMPLETED, targetStatus);
        }
    }

    @Test
    void createTournamentAcceptsOwnerHorseLimitOneToFive() {
        TournamentRequest request = tournamentRequest();
        request.setMinHorsesPerOwner(1);
        request.setMaxHorsesPerOwner(5);

        TournamentResponse response = service.createTournament(ADMIN_ID, request);

        assertEquals(1, response.getMinHorsesPerOwner());
        assertEquals(5, response.getMaxHorsesPerOwner());
    }

    @Test
    void createTournamentRejectsMinHorsesPerOwnerNotPositive() {
        TournamentRequest request = tournamentRequest();
        request.setMinHorsesPerOwner(0);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.createTournament(ADMIN_ID, request));

        assertEquals("Minimum horses per owner must be greater than zero", exception.getMessage());
    }

    @Test
    void createTournamentRejectsMaxHorsesPerOwnerNotPositive() {
        TournamentRequest request = tournamentRequest();
        request.setMaxHorsesPerOwner(0);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.createTournament(ADMIN_ID, request));

        assertEquals("Maximum horses per owner must be greater than zero", exception.getMessage());
    }

    @Test
    void createTournamentRejectsMinHorsesPerOwnerAboveMax() {
        TournamentRequest request = tournamentRequest();
        request.setMinHorsesPerOwner(6);
        request.setMaxHorsesPerOwner(5);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.createTournament(ADMIN_ID, request));

        assertEquals("Minimum horses per owner must not exceed maximum horses per owner", exception.getMessage());
    }

    @Test
    void updateTournamentAcceptsOwnerHorseLimitOneToFive() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setMinHorsesPerOwner(1);
        request.setMaxHorsesPerOwner(5);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournament(ADMIN_ID, 3L, request);

        assertEquals(1, response.getMinHorsesPerOwner());
        assertEquals(5, response.getMaxHorsesPerOwner());
    }

    @Test
    void updateTournamentRejectsMinHorsesPerOwnerNotPositive() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setMinHorsesPerOwner(0);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.updateTournament(ADMIN_ID, 3L, request));

        assertEquals("Minimum horses per owner must be greater than zero", exception.getMessage());
    }

    @Test
    void updateTournamentRejectsMaxHorsesPerOwnerNotPositive() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setMaxHorsesPerOwner(0);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.updateTournament(ADMIN_ID, 3L, request));

        assertEquals("Maximum horses per owner must be greater than zero", exception.getMessage());
    }

    @Test
    void updateTournamentRejectsInvalidHorsesPerOwnerRange() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setMinHorsesPerOwner(6);
        request.setMaxHorsesPerOwner(5);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.updateTournament(ADMIN_ID, 3L, request));

        assertEquals("Minimum horses per owner must not exceed maximum horses per owner", exception.getMessage());
    }

    @Test
    void closeRegistrationRejectsOwnerBelowMinimumHorses() {
        Tournament tournament = tournament(TournamentStatus.OPEN_REGISTRATION);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));
        when(raceRegistrationRepository.countByOwnerForTournament(any(), anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{2L, "owner", 3L}));

        assertThrows(BadRequestException.class,
                () -> service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.REGISTRATION_CLOSED));
    }

    @Test
    void addRaceAcceptsNoPrizesForDraftTournament() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Draft without prizes");
        request.setPrizes(List.of());
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(1, tournament.getRaces().size());
        assertEquals(0, tournament.getRaces().get(0).getPrizes().size());
        assertEquals(0, response.getRaces().get(0).getPrizes().size());
    }

    @Test
    void addRaceIgnoresEmptyPrizePlaceholderForDraftTournament() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Draft placeholder prize");
        request.setPrizes(List.of(prizeRequest(1, "0")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(0, tournament.getRaces().get(0).getPrizes().size());
        assertEquals(0, response.getRaces().get(0).getPrizes().size());
    }

    @Test
    void addRaceAcceptsPrizeAmountsDescendingByRank() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Descending prizes");
        request.setPrizes(List.of(
                prizeRequest(1, "1000"),
                prizeRequest(2, "800"),
                prizeRequest(3, "500"),
                prizeRequest(4, "100")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(4, tournament.getRaces().get(0).getPrizes().size());
    }

    @Test
    void addRaceRejectsEqualPrizeAmountsAcrossRanks() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Equal prizes");
        request.setPrizes(List.of(
                prizeRequest(1, "1000"),
                prizeRequest(2, "1000"),
                prizeRequest(3, "500")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        assertThrows(BadRequestException.class, () -> service.addTournamentRace(ADMIN_ID, 3L, request));
    }

    @Test
    void addRaceRejectsIncreasingPrizeAmountsAcrossRanks() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Increasing prizes");
        request.setPrizes(List.of(
                prizeRequest(1, "1000"),
                prizeRequest(2, "1200"),
                prizeRequest(3, "500")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        assertThrows(BadRequestException.class, () -> service.addTournamentRace(ADMIN_ID, 3L, request));
    }

    @Test
    void publishTournamentRejectsRaceWithoutPrizes() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race race = race(10L, tournament, RaceStatus.DRAFT, "No prize race");
        race.replacePrizes(List.of());
        tournament.getRaces().add(race);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.PUBLISHED));

        assertEquals("Race must have at least one prize", exception.getMessage());
    }

    @Test
    void addRaceAcceptsZeroPrizeAmountEvenWithItemName() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Zero item prize");
        RacePrizeRequest prize = prizeRequest(1, "0");
        prize.setItemName("Trophy");
        request.setPrizes(List.of(prize));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.addTournamentRace(ADMIN_ID, 3L, request);

        assertEquals(BigDecimal.ZERO, tournament.getRaces().get(0).getPrizes().get(0).getAmount());
        assertEquals(BigDecimal.ZERO, response.getRaces().get(0).getPrizes().get(0).getAmount());
    }

    @Test
    void addRaceRejectsNegativePrizeAmount() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        RaceRequest request = raceRequest("Negative prize");
        request.setPrizes(List.of(prizeRequest(1, "-1")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> service.addTournamentRace(ADMIN_ID, 3L, request));

        assertEquals("Race prize amount must not be negative", exception.getMessage());
    }

    @Test
    void updateDraftRaceIgnoresEmptyPrizePlaceholder() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race race = race(10L, tournament, RaceStatus.DRAFT, "Before");
        RaceRequest request = raceRequest("After");
        request.setPrizes(List.of(prizeRequest(1, "0")));
        tournament.getRaces().add(race);
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        TournamentResponse response = service.updateTournamentRace(ADMIN_ID, 10L, request);

        assertEquals(0, race.getPrizes().size());
        assertEquals(0, response.getRaces().get(0).getPrizes().size());
    }

    @Test
    void updatePublishedRaceAcceptsZeroPrizeAmount() {
        Tournament tournament = tournament(TournamentStatus.PUBLISHED);
        Race race = race(10L, tournament, RaceStatus.PUBLISHED, "Before");
        RaceRequest request = raceRequest("After");
        request.setPrizes(List.of(prizeRequest(1, "0")));
        tournament.getRaces().add(race);
        when(raceRepository.findById(10L)).thenReturn(Optional.of(race));

        TournamentResponse response = service.updateTournamentRace(ADMIN_ID, 10L, request);

        assertEquals(BigDecimal.ZERO, race.getPrizes().get(0).getAmount());
        assertEquals(BigDecimal.ZERO, response.getRaces().get(0).getPrizes().get(0).getAmount());
    }

    @Test
    void publishTournamentAcceptsZeroPrizeAmount() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        Race race = race(10L, tournament, RaceStatus.DRAFT, "Zero prize race");
        race.replacePrizes(List.of(RacePrize.builder()
                .rank(1)
                .amount(BigDecimal.ZERO)
                .build()));
        tournament.getRaces().add(race);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournamentStatus(ADMIN_ID, 3L, TournamentStatus.PUBLISHED);

        assertEquals(TournamentStatus.PUBLISHED, tournament.getStatus());
        assertEquals(BigDecimal.ZERO, response.getRaces().get(0).getPrizes().get(0).getAmount());
    }

    @Test
    void updateTournamentAcceptsJockeyChallengePrizeAmountsDescendingByRank() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setJockeyChallengeEnabled(true);
        request.setJockeyChallengePrizes(List.of(
                challengePrizeRequest(1, "1000"),
                challengePrizeRequest(2, "800"),
                challengePrizeRequest(4, "100")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        TournamentResponse response = service.updateTournament(ADMIN_ID, 3L, request);

        assertEquals(3, response.getJockeyChallengePrizes().size());
    }

    @Test
    void updateTournamentRejectsJockeyChallengePrizeAmountsNotDescending() {
        Tournament tournament = tournament(TournamentStatus.DRAFT);
        TournamentUpdateRequest request = new TournamentUpdateRequest();
        request.setJockeyChallengeEnabled(true);
        request.setJockeyChallengePrizes(List.of(
                challengePrizeRequest(1, "1000"),
                challengePrizeRequest(2, "1000"),
                challengePrizeRequest(3, "500")));
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        assertThrows(BadRequestException.class, () -> service.updateTournament(ADMIN_ID, 3L, request));
    }

    private Tournament tournament(TournamentStatus status) {
        return Tournament.builder()
                .id(3L)
                .name("Summer Cup")
                .location("Ho Chi Minh City")
                .province(province())
                .registrationOpenAt(LocalDateTime.of(2026, 7, 1, 9, 0))
                .registrationCloseAt(LocalDateTime.of(2026, 7, 5, 17, 0))
                .startAt(LocalDateTime.of(2026, 7, 10, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 10, 18, 0))
                .minTeams(1)
                .maxTeams(20)
                .minHorsesPerOwner(4)
                .maxHorsesPerOwner(10)
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
                .venue(venue())
                .scheduledStartAt(LocalDateTime.of(2026, 7, 10, 10, id.intValue() % 10))
                .scheduledEndAt(LocalDateTime.of(2026, 7, 10, 11, id.intValue() % 10))
                .minParticipants(1)
                .maxParticipants(8)
                .entryFee(BigDecimal.ZERO)
                .lateCheckInFee(new BigDecimal("500000"))
                .status(status)
                .build();
        race.replacePrizes(List.of(RacePrize.builder()
                .rank(1)
                .amount(BigDecimal.valueOf(100))
                .build()));
        return race;
    }

    private void assertInvalidStatusTransition(TournamentStatus currentStatus, TournamentStatus targetStatus) {
        Tournament tournament = tournament(currentStatus);
        when(tournamentRepository.findById(3L)).thenReturn(Optional.of(tournament));

        assertThrows(BadRequestException.class,
                () -> service.updateTournamentStatus(ADMIN_ID, 3L, targetStatus));
    }

    private RaceRequest raceRequest(String name) {
        RaceRequest request = new RaceRequest();
        request.setName(name);
        request.setDistance("1000m");
        request.setVenueId(VENUE_ID);
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
        return prizeRequest(1, "100");
    }

    private RacePrizeRequest prizeRequest(Integer rank, String amount) {
        RacePrizeRequest request = new RacePrizeRequest();
        request.setRank(rank);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private JockeyChallengePrizeRequest challengePrizeRequest(Integer rank, String amount) {
        JockeyChallengePrizeRequest request = new JockeyChallengePrizeRequest();
        request.setRank(rank);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private String normalizeRaceDistance(String distance) {
        String trimmed = distance.trim().toLowerCase();
        String metersText = trimmed.endsWith("m") ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
        int meters = Integer.parseInt(metersText);
        if (!List.of(1000, 1200, 1500).contains(meters)) {
            throw new BadRequestException("Race distance is not configured");
        }
        return meters + "m";
    }

    private TournamentRequest tournamentRequest() {
        TournamentRequest request = new TournamentRequest();
        request.setName("Summer Cup");
        request.setLocation("Ho Chi Minh City");
        request.setProvinceId(PROVINCE_ID);
        request.setRegistrationOpenAt(LocalDateTime.of(2026, 7, 1, 9, 0));
        request.setRegistrationCloseAt(LocalDateTime.of(2026, 7, 5, 17, 0));
        request.setStartAt(LocalDateTime.of(2026, 7, 10, 9, 0));
        request.setEndAt(LocalDateTime.of(2026, 7, 10, 18, 0));
        request.setMinTeams(1);
        request.setMaxTeams(20);
        request.setMinHorsesPerOwner(4);
        request.setMaxHorsesPerOwner(10);
        return request;
    }

    private Province province() {
        return Province.builder()
                .id(PROVINCE_ID)
                .name("Ho Chi Minh City")
                .code("HCM")
                .active(true)
                .build();
    }

    private RaceVenue venue() {
        return RaceVenue.builder()
                .id(VENUE_ID)
                .province(province())
                .name("Phu Tho Racecourse")
                .active(true)
                .build();
    }

    private RaceVenue otherProvinceVenue() {
        return RaceVenue.builder()
                .id(OTHER_VENUE_ID)
                .province(Province.builder()
                        .id(51L)
                        .name("Ha Noi")
                        .code("HN")
                        .active(true)
                        .build())
                .name("Ha Noi Racecourse")
                .active(true)
                .build();
    }
}
