package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaceSimulationCheckpointResponse {
    private Integer tick;
    private Double at;
    private Double progress;
}
