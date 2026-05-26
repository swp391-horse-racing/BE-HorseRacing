package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NewsArticleUpdateRequest {
    @Size(max = 180, message = "News title must be at most 180 characters")
    private String title;

    @Size(max = 500, message = "News summary must be at most 500 characters")
    private String summary;

    private String content;

    @Size(max = 80, message = "News category must be at most 80 characters")
    private String category;

    private Boolean featured;

    private LocalDateTime publishedAt;
}
