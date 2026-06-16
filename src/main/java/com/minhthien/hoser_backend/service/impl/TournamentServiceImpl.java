package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.JockeyChallengePrizeRequest;
import com.minhthien.hoser_backend.dto.request.RacePrizeRequest;
import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.request.TournamentRequest;
import com.minhthien.hoser_backend.dto.request.TournamentUpdateRequest;
import com.minhthien.hoser_backend.dto.response.JockeyChallengePrizeResponse;
import com.minhthien.hoser_backend.dto.response.RacePrizeResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceVenueResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.dto.response.TournamentSummaryResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.JockeyChallengePrize;
import com.minhthien.hoser_backend.entity.Province;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RacePrize;
import com.minhthien.hoser_backend.entity.RaceVenue;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
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
import com.minhthien.hoser_backend.service.SystemSettingsService;
import com.minhthien.hoser_backend.service.TournamentService;
import com.minhthien.hoser_backend.service.RegistrationOpenBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentServiceImpl implements TournamentService {
    private static final String REFERENCE_TYPE = "TOURNAMENT";
    private static final String TOURNAMENT_BANNER_FOLDER = "hoser/tournaments/banners";

    private final TournamentRepository tournamentRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceRepository raceRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final BetMarketRepository betMarketRepository;
    private final BetRepository betRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceComplaintRepository raceComplaintRepository;
    private final JockeyChallengeResultRepository jockeyChallengeResultRepository;
    private final SystemSettingsService systemSettingsService;
    private final RegistrationOpenBroadcastService registrationOpenBroadcastService;
    private final LocationSettingsService locationSettingsService;

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse createTournament(Long adminId, TournamentRequest request) {
        return createTournament(adminId, request, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse createTournament(Long adminId, TournamentRequest request, MultipartFile banner) {
        User admin = requireAdmin(adminId);
        validateBaseRequest(request);

        Tournament tournament = Tournament.builder()
                .status(TournamentStatus.DRAFT)
                .createdBy(admin.getUsername())
                .updatedBy(admin.getUsername())
                .build();
        var settings = systemSettingsService.getCurrent();
        tournament.setRules(hasText(request.getRules())
                ? request.getRules().trim()
                : settings.getDefaultTournamentRules());
        applyRequest(tournament, request, admin.getUsername());
        applyBanner(tournament, banner);
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_CREATED", saved, "Race day draft created");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse updateTournament(Long adminId, Long tournamentId, TournamentUpdateRequest request) {
        return updateTournament(adminId, tournamentId, request, null);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse updateTournament(Long adminId, Long tournamentId, TournamentUpdateRequest request,
                                               MultipartFile banner) {
        User admin = requireAdmin(adminId);
        if (request == null) {
            throw new BadRequestException("Tournament request is required");
        }
        Tournament tournament = requireTournament(tournamentId);
        requireConfigEditable(tournament);

        applyUpdateRequest(tournament, request, admin.getUsername());
        applyBanner(tournament, banner);
        validateBaseTournament(tournament);
        validateConfiguredRaces(tournament);
        validateConfiguredChallenge(tournament);
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_UPDATED", saved, "Race day setup updated");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public void deleteTournament(Long adminId, Long tournamentId) {
        User admin = requireAdmin(adminId);
        Tournament tournament = requireTournament(tournamentId);
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new BadRequestException("Only draft tournaments can be deleted");
        }
        if (hasTournamentActivity(tournamentId)) {
            throw new BadRequestException("Cannot delete tournament with race activity");
        }
        recordAudit(admin, "TOURNAMENT_DELETED", tournament, "Tournament deleted");
        tournamentRepository.delete(tournament);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse addTournamentRace(Long adminId, Long tournamentId, RaceRequest request) {
        User admin = requireAdmin(adminId);
        if (request == null) {
            throw new BadRequestException("Race request is required");
        }
        Tournament tournament = requireTournament(tournamentId);
        requireConfigEditable(tournament);

        Race race = mapRace(request, tournament);
        race.setTournament(tournament);
        tournament.getRaces().add(race);
        tournament.setUpdatedBy(admin.getUsername());
        validateBaseTournament(tournament);
        validateConfiguredRaces(tournament);
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_RACE_CREATED", saved, "Race created for race day");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse updateTournamentRace(Long adminId, Long raceId, RaceRequest request) {
        User admin = requireAdmin(adminId);
        if (request == null) {
            throw new BadRequestException("Race request is required");
        }
        Race race = requireRace(raceId);
        Tournament tournament = race.getTournament();
        requireConfigEditable(tournament);

        applyRaceRequest(race, request);
        tournament.setUpdatedBy(admin.getUsername());
        validateBaseTournament(tournament);
        validateConfiguredRaces(tournament);
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_RACE_UPDATED", saved, "Race updated: " + raceId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse deleteTournamentRace(Long adminId, Long raceId) {
        User admin = requireAdmin(adminId);
        Race race = requireRace(raceId);
        Tournament tournament = race.getTournament();
        requireConfigEditable(tournament);
        if (race.getStatus() != RaceStatus.DRAFT) {
            throw new BadRequestException("Only draft races can be deleted");
        }
        if (hasRaceActivity(raceId)) {
            throw new BadRequestException("Cannot delete race with registrations or results");
        }

        tournament.getRaces().removeIf(existing -> existing.getId() != null && existing.getId().equals(raceId));
        tournament.setUpdatedBy(admin.getUsername());
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_RACE_DELETED", saved, "Race deleted: " + raceId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse replaceTournamentRaces(Long adminId, Long tournamentId, List<RaceRequest> requests) {
        User admin = requireAdmin(adminId);
        Tournament tournament = requireTournament(tournamentId);
        requireConfigEditable(tournament);

        tournament.replaceRaces(mapRaces(tournament, requests));
        tournament.setUpdatedBy(admin.getUsername());
        validateBaseTournament(tournament);
        validateConfiguredRaces(tournament);
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_RACES_UPDATED", saved, "Race day races replaced");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse openRegistration(Long adminId, Long tournamentId) {
        return updateTournamentStatus(adminId, tournamentId, TournamentStatus.OPEN_REGISTRATION);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse closeRegistration(Long adminId, Long tournamentId) {
        return updateTournamentStatus(adminId, tournamentId, TournamentStatus.REGISTRATION_CLOSED);
    }

    @Override
    @Transactional
    @CacheEvict(value = {
            "adminTournamentSummaries",
            "publicTournamentSummaries",
            "adminTournamentDetails",
            "publicTournamentDetails",
            "publicTournamentRaces"
    }, allEntries = true)
    public TournamentResponse updateTournamentStatus(Long adminId, Long tournamentId, TournamentStatus status) {
        User admin = requireAdmin(adminId);
        if (status == null) {
            throw new BadRequestException("Tournament status is required");
        }
        Tournament tournament = requireTournament(tournamentId);
        TournamentStatus oldStatus = tournament.getStatus();

        validateStatusTransition(oldStatus, status);
        if (requiresReadySetup(status)) {
            validateReadyForPublish(tournament);
        }
        if (status == TournamentStatus.REGISTRATION_CLOSED) {
            validateOwnerHorseMinimums(tournament);
        }
        tournament.setStatus(status);
        if (status == TournamentStatus.PUBLISHED && tournament.getPublishedAt() == null) {
            tournament.setPublishedAt(LocalDateTime.now());
        }
        TournamentStatusSync.syncPreRaceStatuses(tournament, status);
        if (status == TournamentStatus.OPEN_REGISTRATION && tournament.getOpenedRegistrationAt() == null) {
            tournament.setOpenedRegistrationAt(LocalDateTime.now());
        }
        tournament.setUpdatedBy(admin.getUsername());
        Tournament saved = tournamentRepository.save(tournament);
        if (oldStatus != TournamentStatus.OPEN_REGISTRATION
                && status == TournamentStatus.OPEN_REGISTRATION) {
            registrationOpenBroadcastService.broadcast(saved);
        }
        recordAudit(admin, "TOURNAMENT_STATUS_UPDATED", saved,
                "Tournament status changed from " + oldStatus + " to " + status);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "adminTournamentSummaries", key = "#status == null ? 'ALL' : #status.name()")
    public List<TournamentSummaryResponse> getAdminTournaments(TournamentStatus status) {
        List<Tournament> tournaments = status == null
                ? tournamentRepository.findAllByOrderByCreatedAtDesc()
                : tournamentRepository.findByStatusOrderByCreatedAtDesc(status);
        return tournaments.stream().map(this::mapToSummaryResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "adminTournamentDetails", key = "#tournamentId")
    public TournamentResponse getAdminTournament(Long tournamentId) {
        Tournament tournament = requireTournamentDetail(tournamentId);
        List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournamentId);
        return mapToResponse(tournament, races, participantCountsFor(races));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "publicTournamentSummaries", key = "'ALL'")
    public List<TournamentSummaryResponse> getPublicTournaments() {
        return tournamentRepository.findByStatusInOrderByStartAtAsc(publicStatuses()).stream()
                .map(this::mapToSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "publicTournamentDetails", key = "#tournamentId")
    public TournamentResponse getPublicTournament(Long tournamentId) {
        Tournament tournament = requireTournamentDetail(tournamentId);
        if (!isPublicStatus(tournament.getStatus())) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournamentId);
        return mapToResponse(tournament, races, participantCountsFor(races));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "publicTournamentRaces", key = "#tournamentId")
    public List<RaceResponse> getPublicTournamentRaces(Long tournamentId) {
        Tournament tournament = requirePublicTournament(tournamentId);
        List<Race> races = raceRepository.findByTournamentIdOrderByScheduledStartAtAsc(tournamentId);
        Map<Long, Integer> participantCounts = participantCountsFor(races);
        return races.stream()
                .sorted(Comparator.comparing(Race::getScheduledStartAt))
                .map(race -> mapRace(race, participantCounts))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceVenueResponse> getTournamentVenueOptions(Long tournamentId) {
        requireTournament(tournamentId);
        return locationSettingsService.getActiveVenuesByTournament(tournamentId);
    }

    private void applyRequest(Tournament tournament, TournamentRequest request, String updatedBy) {
        tournament.setName(request.getName());
        tournament.setDescription(request.getDescription());
        tournament.setLocation(request.getLocation());
        tournament.setProvince(locationSettingsService.requireActiveProvince(request.getProvinceId()));
        tournament.setBannerUrl(request.getBannerUrl());
        tournament.setRegistrationOpenAt(request.getRegistrationOpenAt());
        tournament.setRegistrationCloseAt(request.getRegistrationCloseAt());
        tournament.setStartAt(request.getStartAt());
        tournament.setEndAt(request.getEndAt());
        tournament.setCheckInDeadlineAt(request.getCheckInDeadlineAt());
        tournament.setEntryFee(BigDecimal.ZERO);
        tournament.setMinTeams(request.getMinTeams());
        tournament.setMaxTeams(request.getMaxTeams());
        tournament.setMinHorsesPerOwner(request.getMinHorsesPerOwner());
        tournament.setMaxHorsesPerOwner(request.getMaxHorsesPerOwner());
        tournament.setJockeyChallengeEnabled(Boolean.TRUE.equals(request.getJockeyChallengeEnabled()));
        tournament.setJockeyChallengeFirstPoints(defaultPositive(request.getJockeyChallengeFirstPoints(), 3));
        tournament.setJockeyChallengeSecondPoints(defaultPositive(request.getJockeyChallengeSecondPoints(), 2));
        tournament.setJockeyChallengeThirdPoints(defaultPositive(request.getJockeyChallengeThirdPoints(), 1));
        tournament.setUpdatedBy(updatedBy);
        tournament.replaceRaces(List.of());
        tournament.replaceJockeyChallengePrizes(mapChallengePrizes(request.getJockeyChallengePrizes()));
    }

    private void applyUpdateRequest(Tournament tournament, TournamentUpdateRequest request, String updatedBy) {
        if (request.getName() != null) {
            tournament.setName(request.getName());
        }
        if (request.getDescription() != null) {
            tournament.setDescription(request.getDescription());
        }
        if (request.getLocation() != null) {
            tournament.setLocation(request.getLocation());
        }
        if (request.getProvinceId() != null) {
            Province province = locationSettingsService.requireActiveProvince(request.getProvinceId());
            validateProvinceChange(tournament, province);
            tournament.setProvince(province);
        }
        if (request.getBannerUrl() != null) {
            tournament.setBannerUrl(request.getBannerUrl());
        }
        if (request.getRegistrationOpenAt() != null) {
            tournament.setRegistrationOpenAt(request.getRegistrationOpenAt());
        }
        if (request.getRegistrationCloseAt() != null) {
            tournament.setRegistrationCloseAt(request.getRegistrationCloseAt());
        }
        if (request.getStartAt() != null) {
            tournament.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            tournament.setEndAt(request.getEndAt());
        }
        if (request.getCheckInDeadlineAt() != null) {
            tournament.setCheckInDeadlineAt(request.getCheckInDeadlineAt());
        }
        if (request.getRules() != null) {
            tournament.setRules(request.getRules().trim());
        }
        if (request.getMinTeams() != null) {
            tournament.setMinTeams(request.getMinTeams());
        }
        if (request.getMaxTeams() != null) {
            tournament.setMaxTeams(request.getMaxTeams());
        }
        if (request.getMinHorsesPerOwner() != null) {
            tournament.setMinHorsesPerOwner(request.getMinHorsesPerOwner());
        }
        if (request.getMaxHorsesPerOwner() != null) {
            tournament.setMaxHorsesPerOwner(request.getMaxHorsesPerOwner());
        }
        if (request.getJockeyChallengeEnabled() != null) {
            tournament.setJockeyChallengeEnabled(request.getJockeyChallengeEnabled());
        }
        if (request.getJockeyChallengeFirstPoints() != null) {
            tournament.setJockeyChallengeFirstPoints(request.getJockeyChallengeFirstPoints());
        }
        if (request.getJockeyChallengeSecondPoints() != null) {
            tournament.setJockeyChallengeSecondPoints(request.getJockeyChallengeSecondPoints());
        }
        if (request.getJockeyChallengeThirdPoints() != null) {
            tournament.setJockeyChallengeThirdPoints(request.getJockeyChallengeThirdPoints());
        }
        if (request.getJockeyChallengePrizes() != null) {
            replaceJockeyChallengePrizesForUpdate(tournament,
                    mapChallengePrizes(request.getJockeyChallengePrizes()));
        }
        tournament.setUpdatedBy(updatedBy);
    }

    @Override
    public String uploadTournamentBanner(Long adminId, MultipartFile banner) {
        requireAdmin(adminId);
        return cloudinaryUploadService.uploadImage(banner, TOURNAMENT_BANNER_FOLDER);
    }

    @Override
    @Transactional
    public TournamentResponse updateTournamentBanner(Long adminId, Long tournamentId, MultipartFile banner) {
        User admin = requireAdmin(adminId);
        Tournament tournament = requireTournament(tournamentId);
        requireConfigEditable(tournament);
        tournament.setBannerUrl(cloudinaryUploadService.uploadImage(banner, TOURNAMENT_BANNER_FOLDER));
        tournament.setUpdatedBy(admin.getUsername());
        Tournament saved = tournamentRepository.save(tournament);
        recordAudit(admin, "TOURNAMENT_BANNER_UPDATED", saved, "Tournament banner updated");
        return mapToResponse(saved);
    }

    private void applyBanner(Tournament tournament, MultipartFile banner) {
        if (banner != null) {
            tournament.setBannerUrl(cloudinaryUploadService.uploadImage(banner, TOURNAMENT_BANNER_FOLDER));
        }
    }

    private void replaceJockeyChallengePrizesForUpdate(Tournament tournament,
                                                       List<JockeyChallengePrize> newPrizes) {
        tournament.getJockeyChallengePrizes().clear();
        tournamentRepository.flush();
        tournament.replaceJockeyChallengePrizes(newPrizes);
    }

    private List<Race> mapRaces(Tournament tournament, List<RaceRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(request -> mapRace(request, tournament))
                .toList();
    }

    private Race mapRace(RaceRequest request, Tournament tournament) {
        var settings = request.getEntryFee() == null || request.getLateCheckInFee() == null
                ? systemSettingsService.getCurrent()
                : null;
        RaceVenue venue = requireVenueForTournament(request.getVenueId(), tournament);
        Race race = Race.builder()
                .name(request.getName())
                .distance(systemSettingsService.normalizeRaceDistance(request.getDistance()))
                .venue(venue)
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledEndAt(request.getScheduledEndAt())
                .minParticipants(request.getMinParticipants())
                .maxParticipants(request.getMaxParticipants())
                .entryFee(request.getEntryFee() == null
                        ? settings.getDefaultRegistrationFee()
                        : request.getEntryFee())
                .lateCheckInFee(request.getLateCheckInFee() == null
                        ? settings.getLateCheckInFee()
                        : request.getLateCheckInFee())
                .referee(request.getRefereeId() == null ? null : requireReferee(request.getRefereeId()))
                .status(RaceStatus.DRAFT)
                .note(request.getNote())
                .build();
        race.replacePrizes(mapRacePrizes(request.getPrizes()));
        return race;
    }

    private void applyRaceRequest(Race race, RaceRequest request) {
        race.setName(request.getName());
        race.setDistance(systemSettingsService.normalizeRaceDistance(request.getDistance()));
        race.setVenue(requireVenueForTournament(request.getVenueId(), race.getTournament()));
        race.setScheduledStartAt(request.getScheduledStartAt());
        race.setScheduledEndAt(request.getScheduledEndAt());
        race.setMinParticipants(request.getMinParticipants());
        race.setMaxParticipants(request.getMaxParticipants());
        if (request.getEntryFee() != null) {
            race.setEntryFee(request.getEntryFee());
        }
        if (request.getLateCheckInFee() != null) {
            race.setLateCheckInFee(request.getLateCheckInFee());
        }
        race.setReferee(request.getRefereeId() == null ? null : requireReferee(request.getRefereeId()));
        race.setNote(request.getNote());
        race.syncPrizes(mapRacePrizes(request.getPrizes()));
    }

    private List<RacePrize> mapRacePrizes(List<RacePrizeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(request -> RacePrize.builder()
                        .rank(request.getRank())
                        .amount(defaultZero(request.getAmount()))
                        .itemName(request.getItemName())
                        .note(request.getNote())
                        .build())
                .toList();
    }

    private List<JockeyChallengePrize> mapChallengePrizes(List<JockeyChallengePrizeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(request -> JockeyChallengePrize.builder()
                        .rank(request.getRank())
                        .amount(defaultZero(request.getAmount()))
                        .note(request.getNote())
                        .build())
                .toList();
    }

    private void validateBaseRequest(TournamentRequest request) {
        if (request == null) {
            throw new BadRequestException("Tournament request is required");
        }
        if (!hasText(request.getName())) {
            throw new BadRequestException("Tournament name is required");
        }
        if (!hasText(request.getLocation())) {
            throw new BadRequestException("Location is required");
        }
        if (request.getProvinceId() == null) {
            throw new BadRequestException("Province is required");
        }
        validateTimeWindow(request.getRegistrationOpenAt(), request.getRegistrationCloseAt(),
                request.getStartAt(), request.getEndAt(), request.getCheckInDeadlineAt());
        validateTeamLimits(request.getMinTeams(), request.getMaxTeams());
        validateHorsesPerOwnerLimits(request.getMinHorsesPerOwner(), request.getMaxHorsesPerOwner());
    }

    private void validateBaseTournament(Tournament tournament) {
        if (!hasText(tournament.getName())) {
            throw new BadRequestException("Tournament name is required");
        }
        if (!hasText(tournament.getLocation())) {
            throw new BadRequestException("Location is required");
        }
        if (tournament.getProvince() == null) {
            throw new BadRequestException("Province is required");
        }
        validateTimeWindow(tournament.getRegistrationOpenAt(), tournament.getRegistrationCloseAt(),
                tournament.getStartAt(), tournament.getEndAt(), tournament.getCheckInDeadlineAt());
        validateTeamLimits(tournament.getMinTeams(), tournament.getMaxTeams());
        validateHorsesPerOwnerLimits(tournament.getMinHorsesPerOwner(), tournament.getMaxHorsesPerOwner());
        tournament.setEntryFee(BigDecimal.ZERO);
    }

    private void validateReadyForPublish(Tournament tournament) {
        validateBaseTournament(tournament);
        if (tournament.getRaces() == null || tournament.getRaces().isEmpty()) {
            throw new BadRequestException("Tournament must have at least one race before publishing");
        }
        validateConfiguredRaces(tournament);
        validateConfiguredChallenge(tournament);
    }

    private void validateConfiguredRaces(Tournament tournament) {
        if (tournament.getRaces() == null || tournament.getRaces().isEmpty()) {
            return;
        }
        Set<String> raceKeys = new HashSet<>();
        for (Race race : tournament.getRaces()) {
            validateRace(tournament, race);
            String key = race.getName().trim().toLowerCase() + "|" + race.getScheduledStartAt();
            if (!raceKeys.add(key)) {
                throw new BadRequestException("Race name and start time must be unique within a tournament");
            }
            validateRacePrizes(race);
        }
    }

    private void validateRace(Tournament tournament, Race race) {
        if (!hasText(race.getName())) {
            throw new BadRequestException("Race name is required");
        }
        if (!hasText(race.getDistance())) {
            throw new BadRequestException("Race distance is required");
        }
        if (race.getVenue() == null) {
            throw new BadRequestException("Race venue is required");
        }
        validateVenueBelongsToTournament(race.getVenue(), tournament);
        if (race.getScheduledStartAt() == null || race.getScheduledEndAt() == null) {
            throw new BadRequestException("Race schedule is required");
        }
        if (!race.getScheduledStartAt().isBefore(race.getScheduledEndAt())) {
            throw new BadRequestException("Race start time must be before end time");
        }
        if (race.getScheduledStartAt().isBefore(tournament.getStartAt())
                || race.getScheduledEndAt().isAfter(tournament.getEndAt())) {
            throw new BadRequestException("Race schedule must be within tournament time window");
        }
        if (race.getMinParticipants() == null || race.getMinParticipants() <= 0
                || race.getMaxParticipants() == null || race.getMaxParticipants() <= 0) {
            throw new BadRequestException("Race participant limits must be greater than zero");
        }
        if (race.getMinParticipants() > race.getMaxParticipants()) {
            throw new BadRequestException("Race minimum participants must not exceed maximum participants");
        }
        requireNonNegative(defaultZero(race.getEntryFee()), "Race entry fee must not be negative");
    }

    private void validateRacePrizes(Race race) {
        if (race.getPrizes() == null || race.getPrizes().isEmpty()) {
            throw new BadRequestException("Race must have at least one prize");
        }
        Set<Integer> ranks = new HashSet<>();
        for (RacePrize prize : race.getPrizes()) {
            if (prize.getRank() == null || prize.getRank() <= 0) {
                throw new BadRequestException("Race prize rank must be greater than zero");
            }
            if (!ranks.add(prize.getRank())) {
                throw new BadRequestException("Race prize rank must be unique within a race");
            }
            BigDecimal amount = defaultZero(prize.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Race prize amount must be greater than zero");
            }
        }
        validatePrizeAmountsDescending(race.getPrizes(), RacePrize::getRank, RacePrize::getAmount,
                "Race prize amounts must decrease as rank increases");
    }

    private void validateConfiguredChallenge(Tournament tournament) {
        if (!Boolean.TRUE.equals(tournament.getJockeyChallengeEnabled())) {
            return;
        }
        if (tournament.getJockeyChallengeFirstPoints() == null || tournament.getJockeyChallengeFirstPoints() <= 0
                || tournament.getJockeyChallengeSecondPoints() == null || tournament.getJockeyChallengeSecondPoints() <= 0
                || tournament.getJockeyChallengeThirdPoints() == null || tournament.getJockeyChallengeThirdPoints() <= 0) {
            throw new BadRequestException("Jockey challenge points must be greater than zero");
        }
        Set<Integer> ranks = new HashSet<>();
        for (JockeyChallengePrize prize : tournament.getJockeyChallengePrizes()) {
            if (prize.getRank() == null || prize.getRank() <= 0) {
                throw new BadRequestException("Jockey challenge prize rank must be greater than zero");
            }
            if (!ranks.add(prize.getRank())) {
                throw new BadRequestException("Jockey challenge prize rank must be unique");
            }
            requireNonNegative(defaultZero(prize.getAmount()), "Jockey challenge prize amount must not be negative");
        }
        validatePrizeAmountsDescending(tournament.getJockeyChallengePrizes(),
                JockeyChallengePrize::getRank, JockeyChallengePrize::getAmount,
                "Jockey challenge prize amounts must decrease as rank increases");
    }

    private <T> void validatePrizeAmountsDescending(List<T> prizes,
                                                    Function<T, Integer> rankExtractor,
                                                    Function<T, BigDecimal> amountExtractor,
                                                    String message) {
        List<T> sortedPrizes = prizes.stream()
                .sorted(Comparator.comparing(rankExtractor))
                .toList();
        for (int i = 1; i < sortedPrizes.size(); i++) {
            T previous = sortedPrizes.get(i - 1);
            T current = sortedPrizes.get(i);
            BigDecimal previousAmount = defaultZero(amountExtractor.apply(previous));
            BigDecimal currentAmount = defaultZero(amountExtractor.apply(current));
            if (previousAmount.compareTo(currentAmount) <= 0) {
                throw new BadRequestException(message);
            }
        }
    }

    private void validateTimeWindow(LocalDateTime registrationOpenAt, LocalDateTime registrationCloseAt,
                                    LocalDateTime startAt, LocalDateTime endAt,
                                    LocalDateTime checkInDeadlineAt) {
        if (registrationOpenAt == null || registrationCloseAt == null || startAt == null || endAt == null) {
            throw new BadRequestException("Tournament time window is required");
        }
        if (!registrationOpenAt.isBefore(registrationCloseAt)) {
            throw new BadRequestException("Registration open time must be before close time");
        }
        if (registrationCloseAt.isAfter(startAt)) {
            throw new BadRequestException("Registration close time must not be after tournament start time");
        }
        if (!startAt.isBefore(endAt)) {
            throw new BadRequestException("Tournament start time must be before end time");
        }
        if (checkInDeadlineAt != null && checkInDeadlineAt.isAfter(startAt)) {
            throw new BadRequestException("Check-in deadline must not be after tournament start time");
        }
    }

    private void validateTeamLimits(Integer minTeams, Integer maxTeams) {
        if (minTeams == null || maxTeams == null) {
            throw new BadRequestException("Tournament team limits are required");
        }
        if (minTeams <= 0) {
            throw new BadRequestException("Minimum teams must be greater than zero");
        }
        if (maxTeams <= 0) {
            throw new BadRequestException("Maximum teams must be greater than zero");
        }
        if (minTeams > maxTeams) {
            throw new BadRequestException("Minimum teams must not exceed maximum teams");
        }
    }

    private void validateHorsesPerOwnerLimits(Integer minHorsesPerOwner, Integer maxHorsesPerOwner) {
        if (minHorsesPerOwner == null || maxHorsesPerOwner == null) {
            throw new BadRequestException("Tournament horses per owner limits are required");
        }
        if (minHorsesPerOwner <= 0) {
            throw new BadRequestException("Minimum horses per owner must be greater than zero");
        }
        if (maxHorsesPerOwner <= 0) {
            throw new BadRequestException("Maximum horses per owner must be greater than zero");
        }
        if (minHorsesPerOwner > maxHorsesPerOwner) {
            throw new BadRequestException("Minimum horses per owner must not exceed maximum horses per owner");
        }
    }

    private void validateOwnerHorseMinimums(Tournament tournament) {
        List<Object[]> ownerCounts = raceRegistrationRepository.countByOwnerForTournament(
                tournament.getId(), activeRegistrationStatuses());
        for (Object[] row : ownerCounts) {
            Long ownerId = (Long) row[0];
            String username = (String) row[1];
            long count = (Long) row[2];
            if (count < tournament.getMinHorsesPerOwner()) {
                throw new BadRequestException("Owner " + username + " (" + ownerId
                        + ") must register at least " + tournament.getMinHorsesPerOwner()
                        + " horses in this tournament");
            }
        }
    }

    private List<RaceRegistrationStatus> activeRegistrationStatuses() {
        return List.of(RaceRegistrationStatus.PENDING, RaceRegistrationStatus.APPROVED);
    }

    private void recordAudit(User admin, String action, Tournament tournament, String reason) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(admin.getId())
                .action(action)
                .referenceType(REFERENCE_TYPE)
                .referenceId(String.valueOf(tournament.getId()))
                .reason(reason)
                .metadata("status=" + tournament.getStatus())
                .build());
    }

    private Tournament requireTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private Tournament requirePublicTournament(Long tournamentId) {
        Tournament tournament = requireTournamentDetail(tournamentId);
        if (!isPublicStatus(tournament.getStatus())) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }
        return tournament;
    }

    private Tournament requireTournamentDetail(Long tournamentId) {
        return tournamentRepository.findDetailById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admins can manage tournaments");
        }
        return admin;
    }

    private User requireReferee(Long refereeId) {
        User referee = userRepository.findById(refereeId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", refereeId));
        if (referee.getRole() != UserRole.REFEREE) {
            throw new BadRequestException("Race referee must have REFEREE role");
        }
        return referee;
    }

    private RaceVenue requireVenueForTournament(Long venueId, Tournament tournament) {
        RaceVenue venue = locationSettingsService.requireActiveVenue(venueId);
        validateVenueBelongsToTournament(venue, tournament);
        return venue;
    }

    private void validateVenueBelongsToTournament(RaceVenue venue, Tournament tournament) {
        if (tournament == null || tournament.getProvince() == null) {
            throw new BadRequestException("Tournament province is required");
        }
        if (venue == null || venue.getProvince() == null
                || !venue.getProvince().getId().equals(tournament.getProvince().getId())) {
            throw new BadRequestException("Race venue must belong to tournament province");
        }
    }

    private void validateProvinceChange(Tournament tournament, Province province) {
        if (tournament.getRaces() == null || tournament.getRaces().isEmpty()) {
            return;
        }
        for (Race race : tournament.getRaces()) {
            RaceVenue venue = race.getVenue();
            if (venue == null || venue.getProvince() == null
                    || !venue.getProvince().getId().equals(province.getId())) {
                throw new BadRequestException("Cannot change tournament province while races use venues from another province");
            }
        }
    }

    private Race requireRace(Long raceId) {
        return raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
    }

    private boolean hasTournamentActivity(Long tournamentId) {
        return raceRegistrationRepository.existsByRaceTournamentId(tournamentId)
                || raceParticipantRepository.existsByRaceTournamentId(tournamentId)
                || betMarketRepository.existsByRaceTournamentId(tournamentId)
                || betRepository.existsByRaceTournamentId(tournamentId)
                || raceResultRepository.existsByRaceTournamentId(tournamentId)
                || raceComplaintRepository.existsByRaceTournamentId(tournamentId)
                || jockeyChallengeResultRepository.existsByTournamentId(tournamentId);
    }

    private boolean hasRaceActivity(Long raceId) {
        return raceRegistrationRepository.existsByRaceId(raceId)
                || raceParticipantRepository.existsByRaceId(raceId)
                || betMarketRepository.existsByRaceId(raceId)
                || betRepository.existsByRaceId(raceId)
                || raceResultRepository.existsByRaceId(raceId)
                || raceComplaintRepository.existsByRaceId(raceId);
    }

    private void requireConfigEditable(Tournament tournament) {
        if (tournament.getStatus() != TournamentStatus.DRAFT && tournament.getStatus() != TournamentStatus.PUBLISHED) {
            throw new BadRequestException("Only draft or published tournaments can be updated");
        }
    }

    public TournamentResponse mapToResponse(Tournament tournament) {
        return mapToResponse(tournament, participantCountsFor(tournament.getRaces()));
    }

    private TournamentResponse mapToResponse(Tournament tournament, Map<Long, Integer> participantCounts) {
        return mapToResponse(tournament, tournament.getRaces(), participantCounts);
    }

    private TournamentResponse mapToResponse(Tournament tournament, List<Race> races,
                                             Map<Long, Integer> participantCounts) {
        return TournamentResponse.builder()
                .id(tournament.getId())
                .name(tournament.getName())
                .description(tournament.getDescription())
                .location(tournament.getLocation())
                .provinceId(tournament.getProvince() == null ? null : tournament.getProvince().getId())
                .provinceName(tournament.getProvince() == null ? null : tournament.getProvince().getName())
                .bannerUrl(tournament.getBannerUrl())
                .registrationOpenAt(tournament.getRegistrationOpenAt())
                .registrationCloseAt(tournament.getRegistrationCloseAt())
                .startAt(tournament.getStartAt())
                .endAt(tournament.getEndAt())
                .checkInDeadlineAt(tournament.getCheckInDeadlineAt())
                .rules(tournament.getRules())
                .minTeams(tournament.getMinTeams())
                .maxTeams(tournament.getMaxTeams())
                .minHorsesPerOwner(tournament.getMinHorsesPerOwner())
                .maxHorsesPerOwner(tournament.getMaxHorsesPerOwner())
                .status(tournament.getStatus())
                .publishedAt(tournament.getPublishedAt())
                .openedRegistrationAt(tournament.getOpenedRegistrationAt())
                .jockeyChallengeEnabled(tournament.getJockeyChallengeEnabled())
                .jockeyChallengeFirstPoints(tournament.getJockeyChallengeFirstPoints())
                .jockeyChallengeSecondPoints(tournament.getJockeyChallengeSecondPoints())
                .jockeyChallengeThirdPoints(tournament.getJockeyChallengeThirdPoints())
                .jockeyChallengeFinalizedAt(tournament.getJockeyChallengeFinalizedAt())
                .jockeyChallengeFinalizedBy(tournament.getJockeyChallengeFinalizedBy())
                .finalizedAt(tournament.getFinalizedAt())
                .finalizedBy(tournament.getFinalizedBy())
                .pendingComplaintCountAtFinalize(tournament.getPendingComplaintCountAtFinalize())
                .races(races.stream()
                        .sorted(Comparator.comparing(Race::getScheduledStartAt))
                        .map(race -> mapRace(race, participantCounts))
                        .toList())
                .jockeyChallengePrizes(tournament.getJockeyChallengePrizes().stream()
                        .sorted(Comparator.comparing(JockeyChallengePrize::getRank))
                        .map(this::mapChallengePrize)
                        .toList())
                .createdAt(tournament.getCreatedAt())
                .updatedAt(tournament.getUpdatedAt())
                .createdBy(tournament.getCreatedBy())
                .updatedBy(tournament.getUpdatedBy())
                .build();
    }

    private TournamentSummaryResponse mapToSummaryResponse(Tournament tournament) {
        return TournamentSummaryResponse.builder()
                .id(tournament.getId())
                .name(tournament.getName())
                .description(tournament.getDescription())
                .location(tournament.getLocation())
                .provinceId(tournament.getProvince() == null ? null : tournament.getProvince().getId())
                .provinceName(tournament.getProvince() == null ? null : tournament.getProvince().getName())
                .bannerUrl(tournament.getBannerUrl())
                .registrationOpenAt(tournament.getRegistrationOpenAt())
                .registrationCloseAt(tournament.getRegistrationCloseAt())
                .startAt(tournament.getStartAt())
                .endAt(tournament.getEndAt())
                .minTeams(tournament.getMinTeams())
                .maxTeams(tournament.getMaxTeams())
                .minHorsesPerOwner(tournament.getMinHorsesPerOwner())
                .maxHorsesPerOwner(tournament.getMaxHorsesPerOwner())
                .status(tournament.getStatus())
                .publishedAt(tournament.getPublishedAt())
                .openedRegistrationAt(tournament.getOpenedRegistrationAt())
                .build();
    }

    public RaceResponse mapRace(Race race) {
        return mapRace(race, Collections.emptyMap());
    }

    private RaceResponse mapRace(Race race, Map<Long, Integer> participantCounts) {
        User referee = race.getReferee();
        RaceVenue venue = race.getVenue();
        Province province = venue == null ? null : venue.getProvince();
        return RaceResponse.builder()
                .id(race.getId())
                .tournamentId(race.getTournament() == null ? null : race.getTournament().getId())
                .name(race.getName())
                .distance(race.getDistance())
                .venueId(venue == null ? null : venue.getId())
                .venueName(venue == null ? null : venue.getName())
                .venueAddress(venue == null ? null : venue.getAddress())
                .provinceId(province == null ? null : province.getId())
                .provinceName(province == null ? null : province.getName())
                .scheduledStartAt(race.getScheduledStartAt())
                .scheduledEndAt(race.getScheduledEndAt())
                .minParticipants(race.getMinParticipants())
                .maxParticipants(race.getMaxParticipants())
                .entryFee(race.getEntryFee())
                .lateCheckInFee(race.getLateCheckInFee())
                .refereeId(referee == null ? null : referee.getId())
                .refereeUsername(referee == null ? null : referee.getUsername())
                .status(race.getStatus())
                .note(race.getNote())
                .resultFinalizedAt(race.getResultFinalizedAt())
                .resultFinalizedBy(race.getResultFinalizedBy())
                .prizes(race.getPrizes().stream()
                        .sorted(Comparator.comparing(RacePrize::getRank))
                        .map(this::mapRacePrize)
                        .toList())
                .participantCount(participantCounts.getOrDefault(race.getId(), 0))
                .createdAt(race.getCreatedAt())
                .updatedAt(race.getUpdatedAt())
                .build();
    }

    private Map<Long, Integer> participantCountsFor(List<Race> races) {
        if (races == null || races.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> raceIds = races.stream()
                .map(Race::getId)
                .filter(id -> id != null)
                .toList();
        if (raceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return raceParticipantRepository.countByRaceIds(raceIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> Math.toIntExact((Long) row[1])
                ));
    }

    private RacePrizeResponse mapRacePrize(RacePrize prize) {
        return RacePrizeResponse.builder()
                .id(prize.getId())
                .rank(prize.getRank())
                .amount(prize.getAmount())
                .itemName(prize.getItemName())
                .note(prize.getNote())
                .createdAt(prize.getCreatedAt())
                .build();
    }

    private JockeyChallengePrizeResponse mapChallengePrize(JockeyChallengePrize prize) {
        return JockeyChallengePrizeResponse.builder()
                .id(prize.getId())
                .rank(prize.getRank())
                .amount(prize.getAmount())
                .note(prize.getNote())
                .createdAt(prize.getCreatedAt())
                .build();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int defaultPositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private void requireNonNegative(BigDecimal value, String message) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isPublicStatus(TournamentStatus status) {
        return publicStatuses().contains(status);
    }

    private boolean requiresReadySetup(TournamentStatus status) {
        return status == TournamentStatus.PUBLISHED || status == TournamentStatus.OPEN_REGISTRATION;
    }

    private void validateStatusTransition(TournamentStatus currentStatus, TournamentStatus targetStatus) {
        boolean allowed = switch (currentStatus) {
            case DRAFT -> targetStatus == TournamentStatus.PUBLISHED
                    || targetStatus == TournamentStatus.CANCELLED;
            case PUBLISHED -> targetStatus == TournamentStatus.OPEN_REGISTRATION
                    || targetStatus == TournamentStatus.CANCELLED;
            case OPEN_REGISTRATION -> targetStatus == TournamentStatus.REGISTRATION_CLOSED;
            case REGISTRATION_CLOSED -> targetStatus == TournamentStatus.SCHEDULED;
            case SCHEDULED -> targetStatus == TournamentStatus.ONGOING;
            case ONGOING, COMPLETED, CANCELLED -> false;
        };
        if (!allowed) {
            if (targetStatus == TournamentStatus.COMPLETED) {
                throw new BadRequestException("Use tournament finalize endpoint to complete tournaments");
            }
            throw new BadRequestException(
                    "Cannot change tournament status from " + currentStatus + " to " + targetStatus);
        }
    }

    private List<TournamentStatus> publicStatuses() {
        return List.of(
                TournamentStatus.PUBLISHED,
                TournamentStatus.OPEN_REGISTRATION,
                TournamentStatus.REGISTRATION_CLOSED,
                TournamentStatus.SCHEDULED,
                TournamentStatus.ONGOING,
                TournamentStatus.COMPLETED
        );
    }
}
