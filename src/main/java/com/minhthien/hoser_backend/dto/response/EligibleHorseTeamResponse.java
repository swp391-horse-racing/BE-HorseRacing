package com.minhthien.hoser_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibleHorseTeamResponse {
    private Long invitationId;
    private Long horseId;
    private String horseName;
    private Long ownerId;
    private String ownerUsername;
    private Long jockeyId;
    private String jockeyUsername;
    private Long jockeyProfileId;
    private String jockeyFullName;
    private LocalDateTime acceptedAt;
}
