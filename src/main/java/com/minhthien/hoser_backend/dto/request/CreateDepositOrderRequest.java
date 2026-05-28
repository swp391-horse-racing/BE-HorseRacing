package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import com.minhthien.hoser_backend.enums.PaymentProvider;

import java.math.BigDecimal;

@Data
public class CreateDepositOrderRequest {
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    private String currency = "VND";

    private PaymentProvider provider;
}
