package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import com.minhthien.hoser_backend.enums.PaymentDepositTarget;
import com.minhthien.hoser_backend.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_orders",
        indexes = {
                @Index(name = "idx_payment_orders_user", columnList = "user_id"),
                @Index(name = "idx_payment_orders_status", columnList = "status"),
                @Index(name = "idx_payment_orders_reference_code", columnList = "reference_code"),
                @Index(name = "idx_payment_orders_order_code", columnList = "order_code"),
                @Index(name = "idx_payment_orders_payment_link", columnList = "payment_link_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_orders_reference_code", columnNames = "reference_code"),
                @UniqueConstraint(name = "uk_payment_orders_order_code", columnNames = "order_code"),
                @UniqueConstraint(name = "uk_payment_orders_payment_link", columnNames = "payment_link_id"),
                @UniqueConstraint(name = "uk_payment_orders_provider_transaction", columnNames = "provider_transaction_id")
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrder {
    public static final String DEFAULT_CURRENCY = "VND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PaymentProvider provider = PaymentProvider.ZALOPAY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_target", length = 30)
    @Builder.Default
    private PaymentDepositTarget depositTarget = PaymentDepositTarget.USER_WALLET;

    @Column(name = "reference_code", nullable = false, length = 80)
    private String referenceCode;

    @Column(name = "provider_transaction_id", length = 150)
    private String providerTransactionId;

    @Column(name = "order_code")
    private Long orderCode;

    @Column(name = "payment_link_id", length = 150)
    private String paymentLinkId;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(length = 255)
    private String transferContent;

    @Column(length = 500)
    private String note;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    private LocalDateTime paidAt;

    private LocalDateTime expiredAt;

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
        if (currency == null || currency.isBlank()) {
            currency = DEFAULT_CURRENCY;
        }
        if (provider == null) {
            provider = PaymentProvider.ZALOPAY;
        }
        if (status == null) {
            status = PaymentOrderStatus.PENDING;
        }
        if (depositTarget == null) {
            depositTarget = PaymentDepositTarget.USER_WALLET;
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
