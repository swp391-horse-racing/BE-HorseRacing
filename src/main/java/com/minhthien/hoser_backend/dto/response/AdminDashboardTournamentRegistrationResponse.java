package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminDashboardTournamentRegistrationResponse {
    private Long tournamentId;
    private String tournamentName;
    private Long raceCount;
    private Long registrationCount;
    private Long capacity;
    private BigDecimal fillRate;
}
