package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaceEligibilityWarning {
    private Long raceId;
    private String raceName;
    private int currentParticipants;
    private int minParticipants;
}
