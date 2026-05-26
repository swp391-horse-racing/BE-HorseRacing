package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NewsArticleResponse {
    private Long id;
    private String title;
    private String summary;
    private String content;
    private String category;
    private String imageUrl;
    private Boolean featured;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
