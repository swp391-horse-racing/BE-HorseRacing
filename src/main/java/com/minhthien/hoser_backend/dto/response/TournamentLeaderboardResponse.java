package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TournamentLeaderboardResponse {
    private Long tournamentId;
    private String tournamentName;
    private TournamentStatus tournamentStatus;
    private LocalDateTime finalizedAt;
    private Long finalizedBy;
    private Integer pendingComplaintCountAtFinalize;
    private List<TournamentLeaderboardEntryResponse> entries;
    private List<JockeyChallengeStandingResponse> jockeyStandings;
}
