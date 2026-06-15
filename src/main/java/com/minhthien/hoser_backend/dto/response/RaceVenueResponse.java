package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RaceVenueResponse {
    private Long id;
    private Long provinceId;
    private String provinceName;
    private String name;
    private String address;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
