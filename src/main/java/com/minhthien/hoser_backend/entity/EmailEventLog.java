package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.EmailEventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "email_event_logs",
        indexes = {
                @Index(name = "idx_email_logs_status", columnList = "status"),
                @Index(name = "idx_email_logs_reference", columnList = "reference_type, reference_id"),
                @Index(name = "idx_email_logs_created", columnList = "created_at")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailEventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String toEmail;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 80)
    private String templateType;

    @Column(name = "reference_type", length = 80)
    private String referenceType;

    @Column(name = "reference_id", length = 80)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailEventStatus status;

    @Column(length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime sentAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
