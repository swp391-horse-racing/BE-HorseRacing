package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.PaymentDepositTarget;
import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import com.minhthien.hoser_backend.enums.PaymentProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    private Long id;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private PaymentProvider provider;
    private PaymentOrderStatus status;
    private PaymentDepositTarget depositTarget;
    private String referenceCode;
    private String providerTransactionId;
    private Long orderCode;
    private String paymentLinkId;
    private String checkoutUrl;
    private String qrCode;
    private String transferContent;
    private LocalDateTime paidAt;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
