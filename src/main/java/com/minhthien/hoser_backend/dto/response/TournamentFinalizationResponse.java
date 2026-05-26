package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TournamentFinalizationResponse {
    private TournamentResponse tournament;
    private TournamentLeaderboardResponse leaderboard;
    private TournamentStatisticsResponse statistics;
    private List<TournamentPayoutResponse> payouts;
}
