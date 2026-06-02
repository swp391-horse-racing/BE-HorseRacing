package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.WithdrawalRequest;
import com.minhthien.hoser_backend.enums.WithdrawalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, Long> {
    List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<WithdrawalRequest> findByIdAndUserId(Long id, Long userId);

    List<WithdrawalRequest> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status);

    List<WithdrawalRequest> findAllByOrderByCreatedAtDesc();

    List<WithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(WithdrawalStatus status);

    @Query("""
            select wr.status, count(wr), coalesce(sum(wr.amount), 0)
            from WithdrawalRequest wr
            where wr.user.id = :userId
            group by wr.status
            """)
    List<Object[]> summarizeByStatusForUser(@Param("userId") Long userId);

    @Query("""
            select wr.status, count(wr), coalesce(sum(wr.amount), 0)
            from WithdrawalRequest wr
            group by wr.status
            """)
    List<Object[]> summarizeByStatus();
}
