package com.minhthien.hoser_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "news_articles",
        indexes = {
                @Index(name = "idx_news_articles_featured", columnList = "featured"),
                @Index(name = "idx_news_articles_category", columnList = "category"),
                @Index(name = "idx_news_articles_published_at", columnList = "published_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 80)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean featured = false;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(length = 100)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (publishedAt == null) {
            publishedAt = now;
        }
        if (featured == null) {
            featured = false;
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "SYSTEM";
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = createdBy;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = "SYSTEM";
        }
        if (featured == null) {
            featured = false;
        }
    }
}
