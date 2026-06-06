package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.WalletTransaction;
import com.minhthien.hoser_backend.enums.WalletTransactionDirection;
import com.minhthien.hoser_backend.enums.WalletOwnerType;
import com.minhthien.hoser_backend.enums.WalletTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
            select wt.type, coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.wallet.id = :walletId
              and wt.direction in :directions
            group by wt.type
            """)
    List<Object[]> sumAmountByTypeForWalletAndDirection(@Param("walletId") Long walletId,
                                                        @Param("directions") List<WalletTransactionDirection> directions);

    @Query("""
            select coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.user.id = :userId
              and wt.type = com.minhthien.hoser_backend.enums.WalletTransactionType.JOCKEY_PAYOUT
            """)
    BigDecimal sumJockeyPayoutByUserId(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.user.id = :userId
              and wt.type = com.minhthien.hoser_backend.enums.WalletTransactionType.PRIZE_PAYOUT
            """)
    BigDecimal sumPrizePayoutByUserId(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.wallet.ownerType = :ownerType
              and wt.direction = :direction
              and wt.status = :status
            """)
    BigDecimal sumAdminRevenue(@Param("ownerType") WalletOwnerType ownerType,
                               @Param("direction") WalletTransactionDirection direction,
                               @Param("status") WalletTransactionStatus status);

    @Query("""
            select coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.wallet.ownerType = :ownerType
              and wt.direction = :direction
              and wt.status = :status
              and wt.createdAt >= :from
              and wt.createdAt < :to
            """)
    BigDecimal sumAdminRevenueBetween(@Param("ownerType") WalletOwnerType ownerType,
                                      @Param("direction") WalletTransactionDirection direction,
                                      @Param("status") WalletTransactionStatus status,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Query("""
            select year(wt.createdAt), month(wt.createdAt), coalesce(sum(wt.amount), 0)
            from WalletTransaction wt
            where wt.wallet.ownerType = :ownerType
              and wt.direction = :direction
              and wt.status = :status
              and wt.createdAt >= :from
              and wt.createdAt < :to
            group by year(wt.createdAt), month(wt.createdAt)
            order by year(wt.createdAt), month(wt.createdAt)
            """)
    List<Object[]> sumAdminRevenueByMonth(@Param("ownerType") WalletOwnerType ownerType,
                                          @Param("direction") WalletTransactionDirection direction,
                                          @Param("status") WalletTransactionStatus status,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);
}
