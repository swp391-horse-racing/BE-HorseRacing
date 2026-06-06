package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.NotificationCampaign;
import com.minhthien.hoser_backend.enums.NotificationAudienceType;
import com.minhthien.hoser_backend.enums.NotificationCampaignStatus;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, Long> {
    @EntityGraph(attributePaths = {"createdBy"})
    @Query("""
            select distinct c
            from NotificationCampaign c
            left join c.channels channel
            where (:status is null or c.status = :status)
              and (:channel is null or channel = :channel)
              and (:audienceType is null or c.audienceType = :audienceType)
            order by c.createdAt desc
            """)
    Page<NotificationCampaign> findFiltered(
            @Param("status") NotificationCampaignStatus status,
            @Param("channel") NotificationChannel channel,
            @Param("audienceType") NotificationAudienceType audienceType,
            Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "channels"})
    @Query("select c from NotificationCampaign c where c.id = :id")
    Optional<NotificationCampaign> findDetailedById(@Param("id") Long id);

    @Query("""
            select c.id
            from NotificationCampaign c
            where c.status = :status
              and c.scheduledAt <= :now
            order by c.scheduledAt asc
            """)
    List<Long> findDueIds(
            @Param("status") NotificationCampaignStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from NotificationCampaign c where c.id = :id")
    Optional<NotificationCampaign> findByIdForUpdate(@Param("id") Long id);
}
