package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProvinceRequest {
    @NotBlank(message = "Province name is required")
    @Size(max = 120, message = "Province name must be at most 120 characters")
    private String name;

    @NotBlank(message = "Province code is required")
    @Size(max = 30, message = "Province code must be at most 30 characters")
    private String code;

    private Boolean active = true;
}
