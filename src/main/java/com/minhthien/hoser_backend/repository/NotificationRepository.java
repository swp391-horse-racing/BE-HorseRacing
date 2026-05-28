package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.Notification;
import com.minhthien.hoser_backend.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    @EntityGraph(attributePaths = {"recipient"})
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    Page<Notification> findByRecipientIdAndReadAtIsNotNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    Page<Notification> findByTypeOrderByCreatedAtDesc(NotificationType type, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    Page<Notification> findByRecipientIdAndTypeOrderByCreatedAtDesc(Long recipientId, NotificationType type,
                                                                    Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"recipient"})
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    Optional<Notification> findByRecipientIdAndTypeAndReferenceTypeAndReferenceId(
            Long recipientId, NotificationType type, String referenceType, String referenceId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);
}
