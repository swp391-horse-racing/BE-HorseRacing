package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceRegistrationStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RaceRegistrationResponse {
    private Long id;
    private Long raceId;
    private String raceName;
    private Long tournamentId;
    private Long ownerId;
    private String ownerUsername;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyUsername;
    private Long jockeyInvitationId;
    private RaceRegistrationStatus status;
    private BigDecimal entryFeeAmount;
    private String ownerNote;
    private String reviewNote;
    private String withdrawNote;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
