package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceResultDraftStatus;
import com.minhthien.hoser_backend.enums.RaceResultSource;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RaceResultDraftResponse {
    private RaceResultDraftStatus status;
    private RaceResultSource source;
    private String simulationRunId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    private List<RaceResultDraftRowResponse> rows;
}
