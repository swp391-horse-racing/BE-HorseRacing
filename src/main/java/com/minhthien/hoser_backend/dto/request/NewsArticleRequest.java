package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NewsArticleRequest {
    @NotBlank(message = "News title is required")
    @Size(max = 180, message = "News title must be at most 180 characters")
    private String title;

    @Size(max = 500, message = "News summary must be at most 500 characters")
    private String summary;

    @NotBlank(message = "News content is required")
    private String content;

    @Size(max = 80, message = "News category must be at most 80 characters")
    private String category;

    private Boolean featured = false;

    private LocalDateTime publishedAt;
}
