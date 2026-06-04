package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.JockeyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "jockey_profiles",
        indexes = {
                @Index(name = "idx_jockey_profiles_user", columnList = "user_id"),
                @Index(name = "idx_jockey_profiles_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_jockey_profiles_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_jockey_profiles_license", columnNames = "license_number")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "license_number", nullable = false, length = 100)
    private String licenseNumber;

    private Integer experienceYears;

    @Column(precision = 8, scale = 2)
    private BigDecimal heightCm;

    @Column(precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(length = 1000)
    private String bio;

    @Column(length = 2000)
    private String awards;

    @Column(length = 2000)
    private String achievements;

    @Column(length = 1000)
    private String specialties;

    @Column(length = 1000)
    private String avatarUrl;

    @Column(length = 1000)
    private String licenseDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JockeyStatus status = JockeyStatus.PENDING;

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
            status = JockeyStatus.PENDING;
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
