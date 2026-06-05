package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndActiveOrderByCreatedAtDesc(UserRole role, Boolean active);
    List<User> findByActive(Boolean active);
    Optional<User> findFirstByRole(UserRole role);
    long countByActive(Boolean active);
    long countByRoleApprovalStatus(RoleApprovalStatus status);

    @Query("""
            select u.role, count(u)
            from User u
            group by u.role
            """)
    List<Object[]> countByRoleGroup();
}
