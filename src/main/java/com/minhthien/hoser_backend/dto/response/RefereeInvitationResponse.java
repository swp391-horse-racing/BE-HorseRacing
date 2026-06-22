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
public class RefereeInvitationResponse {
    private Long id;
    private Long adminId;
    private String adminUsername;
    private Long refereeId;
    private String refereeUsername;
    private Long raceId;
    private String raceName;
    private LocalDateTime raceScheduledStartAt;
    private LocalDateTime raceScheduledEndAt;
    private Long venueId;
    private String venueName;
    private String venueAddress;
    private Long tournamentId;
    private String tournamentName;
    private Long salaryConfigId;
    private String salaryConfigName;
    private String raceType;
    private BigDecimal salaryAmount;
    private AssignmentStatus status;
    private String message;
    private String responseNote;
    private LocalDateTime respondedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
