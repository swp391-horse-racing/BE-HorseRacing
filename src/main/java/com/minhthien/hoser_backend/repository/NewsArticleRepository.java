package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse;
import com.minhthien.hoser_backend.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    List<NewsArticle> findAllByOrderByFeaturedDescPublishedAtDescCreatedAtDesc();

    List<NewsArticle> findByFeaturedOrderByPublishedAtDescCreatedAtDesc(Boolean featured);

    List<NewsArticle> findByCategoryIgnoreCaseOrderByFeaturedDescPublishedAtDescCreatedAtDesc(String category);

    List<NewsArticle> findByCategoryIgnoreCaseAndFeaturedOrderByPublishedAtDescCreatedAtDesc(
            String category, Boolean featured);

    @Query("""
            select new com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse(
                n.id, n.title, n.summary, n.category, n.imageUrl, n.featured,
                n.publishedAt, n.createdAt, n.updatedAt, n.createdBy, n.updatedBy
            )
            from NewsArticle n
            order by n.featured desc, n.publishedAt desc, n.createdAt desc
            """)
    List<NewsArticleSummaryResponse> findAllSummariesOrderByFeaturedDescPublishedAtDescCreatedAtDesc();

    @Query("""
            select new com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse(
                n.id, n.title, n.summary, n.category, n.imageUrl, n.featured,
                n.publishedAt, n.createdAt, n.updatedAt, n.createdBy, n.updatedBy
            )
            from NewsArticle n
            where n.featured = :featured
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<NewsArticleSummaryResponse> findSummaryByFeaturedOrderByPublishedAtDescCreatedAtDesc(
            @Param("featured") Boolean featured);

    @Query("""
            select new com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse(
                n.id, n.title, n.summary, n.category, n.imageUrl, n.featured,
                n.publishedAt, n.createdAt, n.updatedAt, n.createdBy, n.updatedBy
            )
            from NewsArticle n
            where lower(n.category) = lower(:category)
            order by n.featured desc, n.publishedAt desc, n.createdAt desc
            """)
    List<NewsArticleSummaryResponse> findSummaryByCategoryIgnoreCaseOrderByFeaturedDescPublishedAtDescCreatedAtDesc(
            @Param("category") String category);

    @Query("""
            select new com.minhthien.hoser_backend.dto.response.NewsArticleSummaryResponse(
                n.id, n.title, n.summary, n.category, n.imageUrl, n.featured,
                n.publishedAt, n.createdAt, n.updatedAt, n.createdBy, n.updatedBy
            )
            from NewsArticle n
            where lower(n.category) = lower(:category)
              and n.featured = :featured
            order by n.publishedAt desc, n.createdAt desc
            """)
    List<NewsArticleSummaryResponse> findSummaryByCategoryIgnoreCaseAndFeaturedOrderByPublishedAtDescCreatedAtDesc(
            @Param("category") String category, @Param("featured") Boolean featured);
}
