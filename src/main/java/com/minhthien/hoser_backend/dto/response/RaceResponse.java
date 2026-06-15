package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.RaceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RaceResponse {
    private Long id;
    private Long tournamentId;
    private String name;
    private String distance;
    private Long venueId;
    private String venueName;
    private String venueAddress;
    private Long provinceId;
    private String provinceName;
    private LocalDateTime scheduledStartAt;
    private LocalDateTime scheduledEndAt;
    private Integer minParticipants;
    private Integer maxParticipants;
    private BigDecimal entryFee;
    private BigDecimal lateCheckInFee;
    private Long refereeId;
    private String refereeUsername;
    private RaceStatus status;
    private String note;
    private LocalDateTime resultFinalizedAt;
    private Long resultFinalizedBy;
    private List<RacePrizeResponse> prizes;
    private Integer participantCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
