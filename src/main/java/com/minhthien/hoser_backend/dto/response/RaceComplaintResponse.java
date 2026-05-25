package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RaceComplaintResponse {
    private Long id;
    private Long raceId;
    private String raceName;
    private Long complainantOwnerId;
    private Long accusedOwnerId;
    private String accusedOwnerUsername;
    private Long accusedParticipantId;
    private Long accusedHorseId;
    private String accusedHorseName;
    private RaceComplaintStatus status;
    private String reason;
    private String evidenceUrl;
    private String adminNote;
    private BigDecimal ownerPrizeReturnAmount;
    private BigDecimal fineAmount;
    private BigDecimal totalPenaltyAmount;
    private LocalDateTime banUntil;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private Long resolvedBy;
}
