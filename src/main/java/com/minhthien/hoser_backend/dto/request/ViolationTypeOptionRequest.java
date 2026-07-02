package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ViolationTypeOptionRequest {
    @Size(max = 80, message = "Violation type code must be at most 80 characters")
    private String code;

    @Size(max = 100, message = "Violation type label must be at most 100 characters")
    private String label;

    private Boolean active = true;
}
