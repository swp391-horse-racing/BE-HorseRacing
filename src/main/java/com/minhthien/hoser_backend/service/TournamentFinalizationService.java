package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.TournamentFinalizationResponse;
import com.minhthien.hoser_backend.dto.response.TournamentLeaderboardResponse;
import com.minhthien.hoser_backend.dto.response.TournamentPayoutResponse;
import com.minhthien.hoser_backend.dto.response.TournamentStatisticsResponse;

import java.util.List;

public interface TournamentFinalizationService {
    TournamentFinalizationResponse finalizeTournament(Long adminId, Long tournamentId);

    TournamentLeaderboardResponse getLeaderboard(Long tournamentId);

    TournamentStatisticsResponse getStatistics(Long adminId, Long tournamentId);

    List<TournamentPayoutResponse> getPayouts(Long adminId, Long tournamentId);
}
