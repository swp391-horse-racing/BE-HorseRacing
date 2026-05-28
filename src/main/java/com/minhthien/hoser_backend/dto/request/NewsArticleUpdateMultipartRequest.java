package com.minhthien.hoser_backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Data
public class NewsArticleUpdateMultipartRequest {
    @Size(max = 180, message = "News title must be at most 180 characters")
    private String title;

    @Size(max = 500, message = "News summary must be at most 500 characters")
    private String summary;

    private String content;

    @Size(max = 80, message = "News category must be at most 80 characters")
    private String category;

    private Boolean featured;

    private String publishedAt;

    private MultipartFile image;

    public NewsArticleUpdateRequest toNewsArticleUpdateRequest() {
        NewsArticleUpdateRequest request = new NewsArticleUpdateRequest();
        request.setTitle(title);
        request.setSummary(summary);
        request.setContent(content);
        request.setCategory(category);
        request.setFeatured(featured);
        request.setPublishedAt(parsePublishedAt());
        return request;
    }

    private LocalDateTime parsePublishedAt() {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(publishedAt);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(publishedAt).toLocalDateTime();
        }
    }
}
