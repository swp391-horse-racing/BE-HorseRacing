package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminDashboardFeaturedTournamentResponse {
    private Long tournamentId;
    private String name;
    private String bannerUrl;
    private LocalDateTime startAt;
    private TournamentStatus status;
    private Long raceCount;
    private Long registrationCount;
}
