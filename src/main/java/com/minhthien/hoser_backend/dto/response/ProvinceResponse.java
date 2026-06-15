package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProvinceResponse {
    private Long id;
    private String name;
    private String code;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
