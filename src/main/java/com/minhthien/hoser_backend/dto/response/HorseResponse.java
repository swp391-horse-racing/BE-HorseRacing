package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.HorseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseResponse {
    private Long id;
    private Long ownerId;
    private String ownerUsername;
    private String name;
    private String breed;
    private Integer age;
    private String gender;
    private String color;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private String imageUrl;
    private String documentUrl;
    private HorseStatus status;
    private String reviewReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private HorsePerformanceResponse performance;
    private List<HorseRaceHistoryResponse> raceHistory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
