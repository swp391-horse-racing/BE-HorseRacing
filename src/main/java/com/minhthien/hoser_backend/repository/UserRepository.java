package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
    List<User> findByActiveAndRoleNotOrderByIdAsc(Boolean active, UserRole role);
    List<User> findByActiveAndRoleNotAndIdGreaterThanOrderByIdAsc(Boolean active, UserRole role, Long id,
                                                                  Pageable pageable);
    List<User> findByActiveAndRoleOrderByIdAsc(Boolean active, UserRole role);
    Optional<User> findFirstByRole(UserRole role);
    long countByActive(Boolean active);
    long countByActiveAndRoleNot(Boolean active, UserRole role);
    long countByActiveAndRole(Boolean active, UserRole role);
    long countByRoleApprovalStatus(RoleApprovalStatus status);

    @Query("""
            select count(u)
            from User u
            where u.active = true
              and u.role <> :excludedRole
            """)
    long countActiveExcludingRole(@Param("excludedRole") UserRole excludedRole);

    @Query("""
            select count(u)
            from User u
            where u.active = true
              and u.role <> :excludedRole
              and u.createdAt >= :from
              and u.createdAt < :to
            """)
    long countActiveExcludingRoleCreatedBetween(@Param("excludedRole") UserRole excludedRole,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    @Query("""
            select u.role, count(u)
            from User u
            group by u.role
            """)
    List<Object[]> countByRoleGroup();
}
