package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.TwoFactorChallenge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TwoFactorChallengeRepository extends JpaRepository<TwoFactorChallenge, String> {
    @EntityGraph(attributePaths = "user")
    @Query("select c from TwoFactorChallenge c where c.id = :id")
    Optional<TwoFactorChallenge> findDetailedById(@Param("id") String id);
}
