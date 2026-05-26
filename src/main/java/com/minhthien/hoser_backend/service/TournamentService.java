package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.RaceRequest;
import com.minhthien.hoser_backend.dto.request.TournamentRequest;
import com.minhthien.hoser_backend.dto.request.TournamentUpdateRequest;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.TournamentResponse;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TournamentService {
    TournamentResponse createTournament(Long adminId, TournamentRequest request);

    TournamentResponse createTournament(Long adminId, TournamentRequest request, MultipartFile banner);

    TournamentResponse updateTournament(Long adminId, Long tournamentId, TournamentUpdateRequest request);

    TournamentResponse updateTournament(Long adminId, Long tournamentId, TournamentUpdateRequest request,
                                        MultipartFile banner);

    TournamentResponse addTournamentRace(Long adminId, Long tournamentId, RaceRequest request);

    TournamentResponse replaceTournamentRaces(Long adminId, Long tournamentId, List<RaceRequest> requests);

    TournamentResponse openRegistration(Long adminId, Long tournamentId);

    TournamentResponse closeRegistration(Long adminId, Long tournamentId);

    TournamentResponse updateTournamentStatus(Long adminId, Long tournamentId, TournamentStatus status);

    List<TournamentResponse> getAdminTournaments(TournamentStatus status);

    TournamentResponse getAdminTournament(Long tournamentId);

    List<TournamentResponse> getPublicTournaments();

    TournamentResponse getPublicTournament(Long tournamentId);

    List<RaceResponse> getPublicTournamentRaces(Long tournamentId);
}
