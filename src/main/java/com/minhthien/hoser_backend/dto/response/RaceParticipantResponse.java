package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Builder
public class RaceParticipantResponse {
    private Long id;
    private Long raceId;
    private Long registrationId;
    private Long ownerId;
    private String ownerUsername;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyUsername;
    private Integer gateNumber;
    private RaceParticipantStatus status;
    private String checkInNote;
    private LocalDateTime checkedInAt;
    private Long checkedInBy;
    private BigDecimal lateCheckInFeeAmount;
    private Boolean lateCheckInFeeCharged;
    private LocalDateTime createdAt;
}
