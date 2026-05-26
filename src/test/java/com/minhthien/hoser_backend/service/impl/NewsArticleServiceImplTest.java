package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.NewsArticleRequest;
import com.minhthien.hoser_backend.dto.request.NewsArticleUpdateRequest;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.NewsArticle;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.NewsArticleRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsArticleServiceImplTest {
    @Mock
    private NewsArticleRepository newsArticleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @Mock
    private CloudinaryUploadService cloudinaryUploadService;

    @Test
    void adminCreatesNewsJson() {
        NewsArticleServiceImpl service = service();
        User admin = admin();
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(newsArticleRepository.save(any(NewsArticle.class))).thenAnswer(invocation -> {
            NewsArticle article = invocation.getArgument(0);
            article.setId(10L);
            return article;
        });

        var response = service.createNews(9L, request());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getTitle()).isEqualTo("Race Day Preview");
        assertThat(response.getFeatured()).isTrue();
        assertThat(response.getCreatedBy()).isEqualTo("admin");

        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("NEWS_CREATED");
    }

    @Test
    void adminCreatesNewsWithImage() {
        NewsArticleServiceImpl service = service();
        MockMultipartFile image = new MockMultipartFile("image", "news.jpg", "image/jpeg", "img".getBytes());
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(cloudinaryUploadService.uploadImage(image, "hoser/news/images"))
                .thenReturn("https://cdn.example/news.jpg");
        when(newsArticleRepository.save(any(NewsArticle.class))).thenAnswer(invocation -> {
            NewsArticle article = invocation.getArgument(0);
            article.setId(10L);
            return article;
        });

        var response = service.createNews(9L, request(), image);

        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/news.jpg");
        verify(cloudinaryUploadService).uploadImage(image, "hoser/news/images");
    }

    @Test
    void updateNewsWithoutImageKeepsExistingImage() {
        NewsArticleServiceImpl service = service();
        NewsArticle article = article(10L, "Old title", false, LocalDateTime.of(2026, 5, 20, 8, 0));
        article.setImageUrl("https://cdn.example/old.jpg");
        NewsArticleUpdateRequest request = new NewsArticleUpdateRequest();
        request.setTitle("Updated title");

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(newsArticleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(newsArticleRepository.save(any(NewsArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateNews(9L, 10L, request);

        assertThat(response.getTitle()).isEqualTo("Updated title");
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/old.jpg");
    }

    @Test
    void updateNewsWithImageReplacesImage() {
        NewsArticleServiceImpl service = service();
        NewsArticle article = article(10L, "Old title", false, LocalDateTime.of(2026, 5, 20, 8, 0));
        article.setImageUrl("https://cdn.example/old.jpg");
        NewsArticleUpdateRequest request = new NewsArticleUpdateRequest();
        request.setTitle("Updated title");
        MockMultipartFile image = new MockMultipartFile("image", "new.jpg", "image/jpeg", "img".getBytes());

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(newsArticleRepository.findById(10L)).thenReturn(Optional.of(article));
        when(cloudinaryUploadService.uploadImage(image, "hoser/news/images"))
                .thenReturn("https://cdn.example/new.jpg");
        when(newsArticleRepository.save(any(NewsArticle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateNews(9L, 10L, request, image);

        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/new.jpg");
        verify(cloudinaryUploadService).uploadImage(image, "hoser/news/images");
    }

    @Test
    void deleteNewsDeletesRecordAndAudits() {
        NewsArticleServiceImpl service = service();
        NewsArticle article = article(10L, "Old title", false, LocalDateTime.of(2026, 5, 20, 8, 0));

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin()));
        when(newsArticleRepository.findById(10L)).thenReturn(Optional.of(article));

        service.deleteNews(9L, 10L);

        verify(newsArticleRepository).delete(article);
        ArgumentCaptor<AdminAuditLog> auditCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("NEWS_DELETED");
    }

    @Test
    void publicNewsListUsesFeaturedAndNewestOrdering() {
        NewsArticleServiceImpl service = service();
        NewsArticle featured = article(1L, "Featured", true, LocalDateTime.of(2026, 5, 21, 8, 0));
        NewsArticle newest = article(2L, "Newest", false, LocalDateTime.of(2026, 5, 22, 8, 0));
        when(newsArticleRepository.findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc())
                .thenReturn(List.of(featured, newest));

        var response = service.getPublicNews(null, null);

        assertThat(response).extracting("title").containsExactly("Featured", "Newest");
    }

    @Test
    void allPublicNewsUsesFeaturedAndNewestOrdering() {
        NewsArticleServiceImpl service = service();
        NewsArticle featured = article(1L, "Featured", true, LocalDateTime.of(2026, 5, 21, 8, 0));
        NewsArticle newest = article(2L, "Newest", false, LocalDateTime.of(2026, 5, 22, 8, 0));
        when(newsArticleRepository.findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc())
                .thenReturn(List.of(featured, newest));

        var response = service.getAllPublicNews();

        assertThat(response).extracting("title").containsExactly("Featured", "Newest");
    }

    @Test
    void publicGetNewsRejectsMissingArticle() {
        NewsArticleServiceImpl service = service();
        when(newsArticleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicNews(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("News not found with id: '99'");
    }

    private NewsArticleServiceImpl service() {
        return new NewsArticleServiceImpl(newsArticleRepository, userRepository, adminAuditLogRepository,
                cloudinaryUploadService);
    }

    private NewsArticleRequest request() {
        NewsArticleRequest request = new NewsArticleRequest();
        request.setTitle("Race Day Preview");
        request.setSummary("Highlights for the next race day");
        request.setContent("Full article content");
        request.setCategory("Su kien");
        request.setFeatured(true);
        request.setPublishedAt(LocalDateTime.of(2026, 5, 20, 8, 0));
        return request;
    }

    private NewsArticle article(Long id, String title, boolean featured, LocalDateTime publishedAt) {
        return NewsArticle.builder()
                .id(id)
                .title(title)
                .summary("Summary")
                .content("Content")
                .category("Su kien")
                .featured(featured)
                .publishedAt(publishedAt)
                .createdBy("admin")
                .updatedBy("admin")
                .build();
    }

    private User admin() {
        return User.builder()
                .id(9L)
                .username("admin")
                .email("admin@example.com")
                .role(UserRole.ADMIN)
                .build();
    }
}
