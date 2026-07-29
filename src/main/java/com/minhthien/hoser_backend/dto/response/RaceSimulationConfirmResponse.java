package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaceSimulationConfirmResponse {
    private RaceSimulationResponse simulation;
    private RaceResultDraftResponse resultDraft;
}
