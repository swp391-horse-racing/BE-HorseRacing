package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemBrandingSettingsRequest {
    @NotBlank(message = "System name is required")
    @Size(max = 100, message = "System name must not exceed 100 characters")
    private String systemName;

    @NotBlank(message = "Primary color is required")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Primary color must use #RRGGBB format")
    private String primaryColor;
}
