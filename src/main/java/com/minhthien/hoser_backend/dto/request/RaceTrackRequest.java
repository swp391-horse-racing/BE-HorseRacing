package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RaceTrackRequest {
    @NotBlank(message = "Race track name is required")
    @Size(max = 160, message = "Race track name must be at most 160 characters")
    private String name;

    @NotBlank(message = "Location key is required")
    @Size(max = 50, message = "Location key must be at most 50 characters")
    private String locationKey;

    @NotBlank(message = "Location name is required")
    @Size(max = 160, message = "Location name must be at most 160 characters")
    private String locationName;

    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;

    @Size(max = 80, message = "Track type must be at most 80 characters")
    private String trackType;

    @Size(max = 80, message = "Distance must be at most 80 characters")
    private String distance;

    private Boolean active = true;
}
