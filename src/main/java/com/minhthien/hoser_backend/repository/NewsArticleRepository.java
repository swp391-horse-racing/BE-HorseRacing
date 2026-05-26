package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc();

    List<NewsArticle> findByFeaturedOrderByPublishedAtDescCreatedAtDesc(Boolean featured);

    List<NewsArticle> findByCategoryIgnoreCaseOrderByFeaturedDescPublishedAtDescCreatedAtDesc(String category);

    List<NewsArticle> findByCategoryIgnoreCaseAndFeaturedOrderByPublishedAtDescCreatedAtDesc(
            String category, Boolean featured);
}
