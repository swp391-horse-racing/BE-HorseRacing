package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.KycVerification;
import com.minhthien.hoser_backend.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {
    boolean existsByIdNumberHashAndStatusAndUserIdNot(String idNumberHash, KycStatus status, Long userId);

    Optional<KycVerification> findByIdAndUserId(Long id, Long userId);
}
