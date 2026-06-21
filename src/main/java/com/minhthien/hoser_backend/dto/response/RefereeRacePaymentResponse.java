package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RefereePaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefereeRacePaymentResponse {
    private Long id;
    private Long raceId;
    private String raceName;
    private Long tournamentId;
    private String tournamentName;
    private Long refereeId;
    private String refereeUsername;
    private Long salaryConfigId;
    private String salaryConfigName;
    private String raceType;
    private BigDecimal amount;
    private RefereePaymentStatus status;
    private LocalDateTime heldAt;
    private LocalDateTime paidAt;
    private LocalDateTime releasedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
