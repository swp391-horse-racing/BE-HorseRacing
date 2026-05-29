package com.minhthien.hoser_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardItemResponse {
    private String type;
    private Long id;
    private String title;
    private String status;
    private LocalDateTime at;
    private Map<String, Object> metadata;
}
