package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.RaceViolationType;
import com.minhthien.hoser_backend.enums.ViolationResultAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RaceViolationResponse {
    private Long id;
    private Long raceId;
    private String raceName;
    private Long participantId;
    private Integer gateNumber;
    private Long ownerId;
    private String ownerUsername;
    private Long horseId;
    private String horseName;
    private Long jockeyId;
    private String jockeyUsername;
    private Long refereeId;
    private String refereeUsername;
    private RaceViolationType type;
    private RaceViolationSeverity severity;
    private String description;
    private String penaltyText;
    private LocalDateTime occurredAt;
    private ViolationResultAction resultAction;
    private Long timePenaltyMillis;
    private String evidenceUrl;
    private String evidenceName;
    private String evidenceType;
    private Long evidenceSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
