package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RaceTrackResponse {
    private Long id;
    private String name;
    private String locationKey;
    private String locationName;
    private String address;
    private String trackType;
    private String distance;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
