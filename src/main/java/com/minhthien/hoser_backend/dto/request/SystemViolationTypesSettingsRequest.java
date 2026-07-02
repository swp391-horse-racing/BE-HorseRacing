package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SystemViolationTypesSettingsRequest {
    @Valid
    @NotEmpty(message = "Violation types are required")
    private List<ViolationTypeOptionRequest> types;
}
