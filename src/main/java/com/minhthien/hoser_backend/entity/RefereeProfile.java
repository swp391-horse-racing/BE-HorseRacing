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
        name = "referee_profiles",
        indexes = {
                @Index(name = "idx_referee_profiles_user", columnList = "user_id"),
                @Index(name = "idx_referee_profiles_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_referee_profiles_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_referee_profiles_license", columnNames = "license_number")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefereeProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "license_number", nullable = false, length = 100)
    private String licenseNumber;

    private Integer experienceYears;

    @Column(nullable = false, length = 160)
    private String specialty;

    @Column(length = 1000)
    private String certificationDocumentUrl;

    @Column(length = 1000)
    private String bio;

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
