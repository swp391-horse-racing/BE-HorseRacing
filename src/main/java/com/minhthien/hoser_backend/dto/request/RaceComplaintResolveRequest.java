package com.minhthien.hoser_backend.dto.request;

import com.minhthien.hoser_backend.enums.RaceComplaintStatus;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RaceComplaintResolveRequest {
    private RaceComplaintStatus status;

    private LocalDateTime banUntil;

    @PositiveOrZero(message = "Fine amount must not be negative")
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Size(max = 2000, message = "Admin note must be at most 2000 characters")
    private String adminNote;
}
