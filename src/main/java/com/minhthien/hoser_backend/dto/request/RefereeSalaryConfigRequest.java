package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefereeSalaryConfigRequest {
    @NotBlank(message = "Salary config name is required")
    @Size(max = 120, message = "Salary config name must not exceed 120 characters")
    private String name;

    @NotBlank(message = "Race type is required")
    @Size(max = 100, message = "Race type must not exceed 100 characters")
    private String raceType;

    @NotNull(message = "Salary amount is required")
    @DecimalMin(value = "0.01", message = "Salary amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Active status is required")
    private Boolean active;
}
