package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RaceDistanceOptionResponse {
    private Integer meters;
    private String label;
}
