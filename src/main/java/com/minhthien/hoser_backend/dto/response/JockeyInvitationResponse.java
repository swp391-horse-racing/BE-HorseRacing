package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyInvitationResponse {
    private Long id;
    private Long ownerId;
    private String ownerUsername;
    private Long jockeyId;
    private String jockeyUsername;
    private Long jockeyProfileId;
    private Long horseId;
    private String horseName;
    private Long raceId;
    private String raceName;
    private LocalDateTime raceScheduledStartAt;
    private LocalDateTime raceScheduledEndAt;
    private Long venueId;
    private String venueName;
    private String venueAddress;
    private Long tournamentId;
    private String tournamentName;
    private AssignmentStatus status;
    private String message;
    private String responseNote;
    private BigDecimal remunerationAmount;
    private LocalDateTime respondedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
