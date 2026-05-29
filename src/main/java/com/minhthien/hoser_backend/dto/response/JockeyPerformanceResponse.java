package com.minhthien.hoser_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyPerformanceResponse {
    private Long jockeyId;
    private Long raceCount;
    private Long completedRaceCount;
    private Long firstPlaces;
    private Long secondPlaces;
    private Long thirdPlaces;
    private BigDecimal totalJockeyPayout;
    private BigDecimal totalPrizePayout;
    private List<RaceResponse> recentRaces;
}
