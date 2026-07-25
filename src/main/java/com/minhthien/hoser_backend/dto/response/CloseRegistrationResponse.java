package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CloseRegistrationResponse {
    private TournamentResponse tournament;
    private List<RaceEligibilityWarning> warnings;
    private List<Long> cancelledRaceIds;
    private boolean requiresConfirmation;
}
