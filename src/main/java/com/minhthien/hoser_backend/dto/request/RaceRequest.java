package com.minhthien.hoser_backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class RaceRequest {
    @NotBlank(message = "Race name is required")
    @Size(max = 120, message = "Race name must be at most 120 characters")
    private String name;

    @NotBlank(message = "Race distance is required")
    @Size(max = 80, message = "Race distance must be at most 80 characters")
    private String distance;

    @NotNull(message = "Race venue is required")
    private Long venueId;

    @NotNull(message = "Race start time is required")
    private LocalDateTime scheduledStartAt;

    @NotNull(message = "Race end time is required")
    private LocalDateTime scheduledEndAt;

    @NotNull(message = "Minimum participants is required")
    @Positive(message = "Minimum participants must be greater than zero")
    private Integer minParticipants;

    @NotNull(message = "Maximum participants is required")
    @Positive(message = "Maximum participants must be greater than zero")
    private Integer maxParticipants;

    @PositiveOrZero(message = "Entry fee must not be negative")
    @Schema(description = "Fee charged for this specific race only", example = "0", defaultValue = "0")
    private BigDecimal entryFee;

    @Positive(message = "Late check-in fee must be greater than zero")
    @Schema(description = "Late check-in fee charged for this race only", example = "500000")
    private BigDecimal lateCheckInFee;

    private Long refereeId;

    @Size(max = 1000, message = "Race note must be at most 1000 characters")
    private String note;

    @Valid
    private List<RacePrizeRequest> prizes = new ArrayList<>();
}
