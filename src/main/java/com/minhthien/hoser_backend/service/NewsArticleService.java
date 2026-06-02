package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.NewsArticleRequest;
import com.minhthien.hoser_backend.dto.request.NewsArticleUpdateRequest;
import com.minhthien.hoser_backend.dto.response.NewsArticleResponse;
import com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NewsArticleService {
    NewsArticleResponse createNews(Long adminId, NewsArticleRequest request);

    NewsArticleResponse createNews(Long adminId, NewsArticleRequest request, MultipartFile image);

    NewsArticleResponse updateNews(Long adminId, Long newsId, NewsArticleUpdateRequest request);

    NewsArticleResponse updateNews(Long adminId, Long newsId, NewsArticleUpdateRequest request, MultipartFile image);

    void deleteNews(Long adminId, Long newsId);

    List<NewsArticleSummaryResponse> getAdminNews();

    NewsArticleResponse getAdminNews(Long newsId);

    List<NewsArticleSummaryResponse> getAllPublicNews();

    List<NewsArticleSummaryResponse> getPublicNews(Boolean featured, String category);

    NewsArticleResponse getPublicNews(Long newsId);
}
