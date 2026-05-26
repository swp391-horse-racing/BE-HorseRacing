package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.NewsArticleRequest;
import com.minhthien.hoser_backend.dto.request.NewsArticleUpdateRequest;
import com.minhthien.hoser_backend.dto.response.NewsArticleResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.NewsArticle;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.NewsArticleRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.NewsArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NewsArticleServiceImpl implements NewsArticleService {
    private static final String REFERENCE_TYPE = "NEWS";
    private static final String NEWS_IMAGE_FOLDER = "hoser/news/images";

    private final NewsArticleRepository newsArticleRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final CloudinaryUploadService cloudinaryUploadService;

    @Override
    @Transactional
    public NewsArticleResponse createNews(Long adminId, NewsArticleRequest request) {
        return createNews(adminId, request, null);
    }

    @Override
    @Transactional
    public NewsArticleResponse createNews(Long adminId, NewsArticleRequest request, MultipartFile image) {
        User admin = requireAdmin(adminId);
        validateCreateRequest(request);

        NewsArticle article = NewsArticle.builder()
                .createdBy(admin.getUsername())
                .updatedBy(admin.getUsername())
                .build();
        applyCreateRequest(article, request, admin.getUsername());
        applyImage(article, image);

        NewsArticle saved = newsArticleRepository.save(article);
        recordAudit(admin, "NEWS_CREATED", saved, "News article created");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public NewsArticleResponse updateNews(Long adminId, Long newsId, NewsArticleUpdateRequest request) {
        return updateNews(adminId, newsId, request, null);
    }

    @Override
    @Transactional
    public NewsArticleResponse updateNews(Long adminId, Long newsId, NewsArticleUpdateRequest request,
                                          MultipartFile image) {
        User admin = requireAdmin(adminId);
        if (request == null) {
            throw new BadRequestException("News request is required");
        }
        NewsArticle article = requireNews(newsId);

        applyUpdateRequest(article, request, admin.getUsername());
        applyImage(article, image);
        validateArticle(article);

        NewsArticle saved = newsArticleRepository.save(article);
        recordAudit(admin, "NEWS_UPDATED", saved, "News article updated");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteNews(Long adminId, Long newsId) {
        User admin = requireAdmin(adminId);
        NewsArticle article = requireNews(newsId);
        newsArticleRepository.delete(article);
        recordAudit(admin, "NEWS_DELETED", article, "News article deleted");
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsArticleResponse> getAdminNews() {
        return newsArticleRepository.findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NewsArticleResponse getAdminNews(Long newsId) {
        return mapToResponse(requireNews(newsId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsArticleResponse> getAllPublicNews() {
        return newsArticleRepository.findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NewsArticleResponse> getPublicNews(Boolean featured, String category) {
        List<NewsArticle> articles;
        if (hasText(category) && featured != null) {
            articles = newsArticleRepository
                    .findByCategoryIgnoreCaseAndFeaturedOrderByPublishedAtDescCreatedAtDesc(category.trim(), featured);
        } else if (hasText(category)) {
            articles = newsArticleRepository
                    .findByCategoryIgnoreCaseOrderByFeaturedDescPublishedAtDescCreatedAtDesc(category.trim());
        } else if (featured != null) {
            articles = newsArticleRepository.findByFeaturedOrderByPublishedAtDescCreatedAtDesc(featured);
        } else {
            articles = newsArticleRepository.findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc();
        }
        return articles.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NewsArticleResponse getPublicNews(Long newsId) {
        return mapToResponse(requireNews(newsId));
    }

    private void applyCreateRequest(NewsArticle article, NewsArticleRequest request, String updatedBy) {
        article.setTitle(request.getTitle());
        article.setSummary(request.getSummary());
        article.setContent(request.getContent());
        article.setCategory(request.getCategory());
        article.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        article.setPublishedAt(request.getPublishedAt() == null ? LocalDateTime.now() : request.getPublishedAt());
        article.setUpdatedBy(updatedBy);
    }

    private void applyUpdateRequest(NewsArticle article, NewsArticleUpdateRequest request, String updatedBy) {
        if (request.getTitle() != null) {
            article.setTitle(request.getTitle());
        }
        if (request.getSummary() != null) {
            article.setSummary(request.getSummary());
        }
        if (request.getContent() != null) {
            article.setContent(request.getContent());
        }
        if (request.getCategory() != null) {
            article.setCategory(request.getCategory());
        }
        if (request.getFeatured() != null) {
            article.setFeatured(request.getFeatured());
        }
        if (request.getPublishedAt() != null) {
            article.setPublishedAt(request.getPublishedAt());
        }
        article.setUpdatedBy(updatedBy);
    }

    private void applyImage(NewsArticle article, MultipartFile image) {
        if (image != null) {
            article.setImageUrl(cloudinaryUploadService.uploadImage(image, NEWS_IMAGE_FOLDER));
        }
    }

    private void validateCreateRequest(NewsArticleRequest request) {
        if (request == null) {
            throw new BadRequestException("News request is required");
        }
        if (!hasText(request.getTitle())) {
            throw new BadRequestException("News title is required");
        }
        if (!hasText(request.getContent())) {
            throw new BadRequestException("News content is required");
        }
    }

    private void validateArticle(NewsArticle article) {
        if (!hasText(article.getTitle())) {
            throw new BadRequestException("News title is required");
        }
        if (!hasText(article.getContent())) {
            throw new BadRequestException("News content is required");
        }
        if (article.getPublishedAt() == null) {
            article.setPublishedAt(LocalDateTime.now());
        }
        if (article.getFeatured() == null) {
            article.setFeatured(false);
        }
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admins can manage news");
        }
        return admin;
    }

    private NewsArticle requireNews(Long newsId) {
        return newsArticleRepository.findById(newsId)
                .orElseThrow(() -> new ResourceNotFoundException("News", "id", newsId));
    }

    private void recordAudit(User admin, String action, NewsArticle article, String reason) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(admin.getId())
                .action(action)
                .referenceType(REFERENCE_TYPE)
                .referenceId(String.valueOf(article.getId()))
                .reason(reason)
                .metadata("featured=" + article.getFeatured())
                .build());
    }

    private NewsArticleResponse mapToResponse(NewsArticle article) {
        return NewsArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .content(article.getContent())
                .category(article.getCategory())
                .imageUrl(article.getImageUrl())
                .featured(article.getFeatured())
                .publishedAt(article.getPublishedAt())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .createdBy(article.getCreatedBy())
                .updatedBy(article.getUpdatedBy())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
