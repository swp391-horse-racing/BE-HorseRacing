package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.NotificationDelivery;
import com.minhthien.hoser_backend.enums.NotificationChannel;
import com.minhthien.hoser_backend.enums.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    @EntityGraph(attributePaths = {"recipient"})
    List<NotificationDelivery> findByCampaignIdAndStatusOrderByIdAsc(
            Long campaignId, NotificationDeliveryStatus status);

    @Query("""
            select d.channel, d.status, count(d)
            from NotificationDelivery d
            where d.campaign.id = :campaignId
            group by d.channel, d.status
            """)
    List<Object[]> summarizeByCampaignId(@Param("campaignId") Long campaignId);

    long countByCampaignIdAndChannelAndStatus(
            Long campaignId, NotificationChannel channel, NotificationDeliveryStatus status);
}
