package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RealtimeEventResponse {
    private String eventType;
    private Long raceId;
    private Long tournamentId;
    private String status;
    private String referenceId;
    private LocalDateTime timestamp;
}
