package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByReferenceCode(String referenceCode);

    Optional<PaymentOrder> findByOrderCode(Long orderCode);

    Optional<PaymentOrder> findByPaymentLinkId(String paymentLinkId);

    @EntityGraph(attributePaths = "user")
    Optional<PaymentOrder> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = "user")
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PaymentOrder> findByStatusOrderByCreatedAtDesc(PaymentOrderStatus status);

    @EntityGraph(attributePaths = "user")
    List<PaymentOrder> findAllByOrderByCreatedAtDesc();

    @Query("""
            select po.status, count(po)
            from PaymentOrder po
            group by po.status
            """)
    List<Object[]> countByStatusGroup();
}
