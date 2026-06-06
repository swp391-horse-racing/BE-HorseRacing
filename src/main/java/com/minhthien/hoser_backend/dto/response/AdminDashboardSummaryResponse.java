package com.minhthien.hoser_backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminDashboardSummaryResponse {
    private Long tournamentCount;
    private Long raceCount;
    private Long registrationCount;
    private BigDecimal revenue;
    private AdminDashboardMetricResponse tournament;
    private AdminDashboardMetricResponse race;
    private AdminDashboardMetricResponse activeUser;
    private AdminDashboardMetricResponse revenueMetric;
}
