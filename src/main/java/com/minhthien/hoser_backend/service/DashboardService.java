package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.DashboardResponse;
import com.minhthien.hoser_backend.dto.response.JockeyPerformanceResponse;
import com.minhthien.hoser_backend.dto.response.RaceResponse;
import com.minhthien.hoser_backend.dto.response.WalletTransactionResponse;
import com.minhthien.hoser_backend.enums.RaceStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface DashboardService {
    DashboardResponse getCurrentUserDashboard(Long userId);

    DashboardResponse getOwnerDashboard(Long userId);

    DashboardResponse getJockeyDashboard(Long userId);

    DashboardResponse getRefereeDashboard(Long userId);

    DashboardResponse getSpectatorDashboard(Long userId);

    DashboardResponse getAdminDashboard(Long userId);

    List<RaceResponse> getOwnerRaces(Long userId);

    List<WalletTransactionResponse> getOwnerPrizes(Long userId);

    List<RaceResponse> getJockeyRaces(Long userId);

    JockeyPerformanceResponse getJockeyPerformance(Long userId);

    List<WalletTransactionResponse> getJockeyPrizes(Long userId);

    List<RaceResponse> getAdminRaces(LocalDateTime from, LocalDateTime to, RaceStatus status);
}
