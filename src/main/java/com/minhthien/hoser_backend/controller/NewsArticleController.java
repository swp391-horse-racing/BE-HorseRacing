package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.dto.request.NewsArticleRequest;
import com.minhthien.hoser_backend.dto.request.NewsArticleMultipartRequest;
import com.minhthien.hoser_backend.dto.request.NewsArticleUpdateRequest;
import com.minhthien.hoser_backend.dto.request.NewsArticleUpdateMultipartRequest;
import com.minhthien.hoser_backend.dto.response.ApiResponse;
import com.minhthien.hoser_backend.dto.response.NewsArticleResponse;
import com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.service.NewsArticleService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class NewsArticleController {
    private final NewsArticleService newsArticleService;

    @PostMapping(value = "/admin/news", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<NewsArticleResponse>> createNews(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody NewsArticleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("News created",
                newsArticleService.createNews(currentUser.getId(), request)));
    }

    @PostMapping(value = "/admin/news", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsArticleResponse>> createNewsWithImage(
            @AuthenticationPrincipal User currentUser,
            @Valid @ModelAttribute NewsArticleMultipartRequest request) {
        return ResponseEntity.ok(ApiResponse.success("News created",
                newsArticleService.createNews(
                        currentUser.getId(), request.toNewsArticleRequest(), request.getImage())));
    }

    @PutMapping(value = "/admin/news/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<NewsArticleResponse>> updateNews(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @RequestBody NewsArticleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("News updated",
                newsArticleService.updateNews(currentUser.getId(), id, request)));
    }

    @PutMapping(value = "/admin/news/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<NewsArticleResponse>> updateNewsWithImage(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id,
            @Valid @ModelAttribute NewsArticleUpdateMultipartRequest request) {
        return ResponseEntity.ok(ApiResponse.success("News updated",
                newsArticleService.updateNews(
                        currentUser.getId(), id, request.toNewsArticleUpdateRequest(), request.getImage())));
    }

    @DeleteMapping("/admin/news/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNews(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long id) {
        newsArticleService.deleteNews(currentUser.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("News deleted", null));
    }

    @GetMapping("/admin/news")
    public ResponseEntity<ApiResponse<List<NewsArticleSummaryResponse>>> getAdminNews() {
        return ResponseEntity.ok(ApiResponse.success(newsArticleService.getAdminNews()));
    }

    @GetMapping("/admin/news/{id}")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> getAdminNews(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(newsArticleService.getAdminNews(id)));
    }

    @GetMapping("/news")
    public ResponseEntity<ApiResponse<List<NewsArticleSummaryResponse>>> getPublicNews(
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.success(newsArticleService.getPublicNews(featured, category)));
    }

    @GetMapping("/news/all")
    public ResponseEntity<ApiResponse<List<NewsArticleSummaryResponse>>> getAllPublicNews() {
        return ResponseEntity.ok(ApiResponse.success(newsArticleService.getAllPublicNews()));
    }

    @GetMapping("/news/{id}")
    public ResponseEntity<ApiResponse<NewsArticleResponse>> getPublicNews(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(newsArticleService.getPublicNews(id)));
    }
}
