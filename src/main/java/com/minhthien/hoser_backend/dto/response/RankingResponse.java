package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RankingResponse {
    private LocalDateTime generatedAt;
    private String metric;
    private List<HorseRankingEntryResponse> horses;
    private List<JockeyRankingEntryResponse> jockeys;
}
