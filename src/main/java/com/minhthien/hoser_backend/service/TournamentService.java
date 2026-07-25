package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.request.TournamentRequest;
import com.minhthien.hoser_backend.dto.request.TournamentUpdateRequest;
import com.minhthien.hoser_backend.dto.response.CloseRegistrationResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.RaceVenueResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.dto.response.TournamentSummaryResponse;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TournamentService {
    TournamentResponse createTournament(Long adminId, TournamentRequest request);

    TournamentResponse createTournament(Long adminId, TournamentRequest request, MultipartFile banner);

    TournamentResponse updateTournament(Long adminId, Long tournamentId, TournamentUpdateRequest request);

    TournamentResponse updateTournament(Long adminId, Long tournamentId, TournamentUpdateRequest request,
                                        MultipartFile banner);

    void deleteTournament(Long adminId, Long tournamentId);

    String uploadTournamentBanner(Long adminId, MultipartFile banner);

    TournamentResponse updateTournamentBanner(Long adminId, Long tournamentId, MultipartFile banner);

    TournamentResponse addTournamentRace(Long adminId, Long tournamentId, RaceRequest request);

    TournamentResponse updateTournamentRace(Long adminId, Long raceId, RaceRequest request);

    TournamentResponse deleteTournamentRace(Long adminId, Long raceId);

    TournamentResponse replaceTournamentRaces(Long adminId, Long tournamentId, List<RaceRequest> requests);

    TournamentResponse openRegistration(Long adminId, Long tournamentId);

    CloseRegistrationResponse closeRegistration(Long adminId, Long tournamentId, boolean force);

    TournamentResponse updateTournamentStatus(Long adminId, Long tournamentId, TournamentStatus status);

    List<TournamentSummaryResponse> getAdminTournaments(TournamentStatus status);

    TournamentResponse getAdminTournament(Long tournamentId);

    List<TournamentSummaryResponse> getPublicTournaments();

    TournamentResponse getPublicTournament(Long tournamentId);

    List<RaceResponse> getPublicTournamentRaces(Long tournamentId);

    List<RaceVenueResponse> getTournamentVenueOptions(Long tournamentId);
}
