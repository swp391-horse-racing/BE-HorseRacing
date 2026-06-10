package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_verifications", indexes = {
        @Index(name = "idx_kyc_user", columnList = "user_id"),
        @Index(name = "idx_kyc_status", columnList = "status"),
        @Index(name = "idx_kyc_id_hash", columnList = "id_number_hash")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 30)
    private UserRole requestedRole;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String provider = "FPT_AI";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStatus status;

    @Column(name = "id_number_hash", length = 64)
    private String idNumberHash;

    @Column(name = "id_number_masked", length = 50)
    private String idNumberMasked;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "date_of_birth", length = 50)
    private String dateOfBirth;

    @Column(length = 50)
    private String gender;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "issue_date", length = 50)
    private String issueDate;

    @Column(name = "front_ocr_passed", nullable = false)
    @Builder.Default
    private boolean frontOcrPassed = false;

    @Column(name = "face_matched", nullable = false)
    @Builder.Default
    private boolean faceMatched = false;

    @Column(name = "face_score", precision = 8, scale = 4)
    private BigDecimal faceScore;

    @Column(name = "front_image_url", columnDefinition = "TEXT")
    private String frontImageUrl;

    @Column(name = "back_image_url", columnDefinition = "TEXT")
    private String backImageUrl;

    @Column(name = "selfie_image_url", columnDefinition = "TEXT")
    private String selfieImageUrl;

    @Column(name = "raw_front_response", columnDefinition = "LONGTEXT")
    private String rawFrontResponse;

    @Column(name = "raw_face_response", columnDefinition = "LONGTEXT")
    private String rawFaceResponse;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (provider == null || provider.isBlank()) provider = "FPT_AI";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
