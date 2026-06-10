package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "owner_profiles",
        indexes = {
                @Index(name = "idx_owner_profiles_user", columnList = "user_id"),
                @Index(name = "idx_owner_profiles_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_owner_profiles_user", columnNames = "user_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 160)
    private String stableName;

    private Integer experienceYears;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 1000)
    private String bio;

    @Column(length = 1000)
    private String verificationDocumentUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_verification_id", unique = true)
    private KycVerification kycVerification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoleApprovalStatus status = RoleApprovalStatus.DRAFT;

    @Column(length = 1000)
    private String reviewReason;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(length = 100)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = RoleApprovalStatus.DRAFT;
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "SYSTEM";
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = createdBy;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updatedBy == null || updatedBy.isBlank()) {
            updatedBy = "SYSTEM";
        }
    }
}
