package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDashboardTournamentRaceCountResponse {
    private Long tournamentId;
    private String tournamentName;
    private String shortName;
    private Long raceCount;
}
