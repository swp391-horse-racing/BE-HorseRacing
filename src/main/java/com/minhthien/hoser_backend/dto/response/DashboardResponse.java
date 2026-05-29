package com.minhthien.hoser_backend.dto.response;

import com.minhthien.hoser_backend.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private UserRole role;
    private Map<String, Object> account;
    private WalletResponse wallet;
    private Map<String, BigDecimal> moneyIn;
    private Map<String, BigDecimal> moneyOut;
    private Map<String, BigDecimal> hold;
    private WithdrawalSummary withdrawals;
    private List<WalletTransactionResponse> recentTransactions;
    private List<NotificationResponse> recentNotifications;
    private Map<String, Object> businessSummary;
    private List<DashboardItemResponse> alerts;
    private List<DashboardItemResponse> upcoming;
    private List<DashboardQuickLinkResponse> quickLinks;
    private Map<String, Boolean> featureFlags;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawalSummary {
        private Long total;
        private Map<String, Long> countByStatus;
        private Map<String, BigDecimal> amountByStatus;
    }
}
