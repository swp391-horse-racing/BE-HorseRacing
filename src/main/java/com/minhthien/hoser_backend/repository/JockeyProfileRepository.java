package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JockeyProfileRepository extends JpaRepository<JockeyProfile, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<JockeyProfile> findByUserId(Long userId);

    boolean existsByLicenseNumberAndUserIdNot(String licenseNumber, Long userId);

    @EntityGraph(attributePaths = "user")
    List<JockeyProfile> findByStatusOrderByCreatedAtDesc(JockeyStatus status);

    long countByStatus(JockeyStatus status);

    @EntityGraph(attributePaths = "user")
    List<JockeyProfile> findAllByOrderByCreatedAtDesc();
}
