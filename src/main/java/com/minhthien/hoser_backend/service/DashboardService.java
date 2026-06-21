package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.*;
import com.minhthien.hoser_backend.enums.RaceStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardService {
    DashboardResponse getCurrentUserDashboard(Long userId);

    DashboardResponse getOwnerDashboard(Long userId);

    DashboardResponse getJockeyDashboard(Long userId);

    DashboardResponse getRefereeDashboard(Long userId);

    RefereeCheckInCountResponse getRefereeCheckedInCount(Long userId);

    RefereeCheckInCountResponse getRefereePendingCheckInCount(Long userId);

    DashboardResponse getSpectatorDashboard(Long userId);

    DashboardResponse getAdminDashboard(Long userId);

    List<RaceResponse> getOwnerRaces(Long userId);

    List<WalletTransactionResponse> getOwnerPrizes(Long userId);

    List<RaceResponse> getJockeyRaces(Long userId);

    JockeyPerformanceResponse getJockeyPerformance(Long userId);

    List<WalletTransactionResponse> getJockeyPrizes(Long userId);

    List<RaceResponse> getAdminRaces(LocalDateTime from, LocalDateTime to, RaceStatus status);

    AdminDashboardSummaryResponse getAdminDashboardSummary();

    List<AdminDashboardRevenueResponse> getAdminDashboardRevenue(int months);

    List<AdminDashboardTournamentRegistrationResponse> getAdminTournamentRegistrations();

    List<AdminDashboardTopHorseResponse> getAdminTopHorses(int limit);

    List<AdminDashboardInsightResponse> getAdminQuickInsights(int months);

    List<AdminDashboardTournamentRaceCountResponse> getAdminTournamentRaceCounts(int limit);

    List<AdminDashboardFeaturedTournamentResponse> getAdminFeaturedTournaments(int limit);
}
