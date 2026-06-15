package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RaceVenueRequest {
    @NotBlank(message = "Venue name is required")
    @Size(max = 160, message = "Venue name must be at most 160 characters")
    private String name;

    @Size(max = 500, message = "Venue address must be at most 500 characters")
    private String address;

    private Boolean active = true;
}
