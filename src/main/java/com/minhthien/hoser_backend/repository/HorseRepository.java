package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.enums.HorseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Long> {
    @EntityGraph(attributePaths = "owner")
    List<Horse> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    @EntityGraph(attributePaths = "owner")
    Optional<Horse> findByIdAndOwnerId(Long id, Long ownerId);

    @EntityGraph(attributePaths = "owner")
    Optional<Horse> findByIdAndStatus(Long id, HorseStatus status);

    @EntityGraph(attributePaths = "owner")
    List<Horse> findByStatusOrderByCreatedAtDesc(HorseStatus status);

    long countByStatus(HorseStatus status);

    @EntityGraph(attributePaths = "owner")
    List<Horse> findAllByOrderByCreatedAtDesc();
}
