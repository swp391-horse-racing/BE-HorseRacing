package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.*;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.TournamentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private HorseRepository horseRepository;
    @Mock private RaceRegistrationRepository raceRegistrationRepository;
    @Mock private JockeyInvitationRepository jockeyInvitationRepository;
    @Mock private JockeyProfileRepository jockeyProfileRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private RaceParticipantRepository raceParticipantRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private BetRepository betRepository;
    @Mock private BetMarketRepository betMarketRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentCallbackLogRepository paymentCallbackLogRepository;
    @Mock private AdminWalletWithdrawalRepository adminWalletWithdrawalRepository;
    @Mock private RaceComplaintRepository raceComplaintRepository;
    @Mock private WalletService walletService;

    @InjectMocks
    private DashboardServiceImpl service;

    @Test
    void refereeCheckedInCountOnlyQueriesCheckedInStatus() {
        long refereeId = 7L;
        User referee = User.builder().id(refereeId).role(UserRole.REFEREE).build();
        when(userRepository.findById(refereeId)).thenReturn(Optional.of(referee));
        when(raceParticipantRepository.countByRaceRefereeIdAndStatus(
                refereeId, RaceParticipantStatus.CHECKED_IN)).thenReturn(37L);

        RefereeCheckInCountResponse response = service.getRefereeCheckedInCount(refereeId);

        assertEquals(37L, response.getCount());
    }

    @Test
    void refereePendingCheckInCountOnlyQueriesRegisteredStatus() {
        long refereeId = 7L;
        User referee = User.builder().id(refereeId).role(UserRole.REFEREE).build();
        when(userRepository.findById(refereeId)).thenReturn(Optional.of(referee));
        when(raceParticipantRepository.countByRaceRefereeIdAndStatus(
                refereeId, RaceParticipantStatus.REGISTERED)).thenReturn(23L);

        RefereeCheckInCountResponse response = service.getRefereePendingCheckInCount(refereeId);

        assertEquals(23L, response.getCount());
    }

    @Test
    void refereeCheckInCountsRejectNonRefereeUsers() {
        long userId = 8L;
        User owner = User.builder().id(userId).role(UserRole.OWNER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(owner));

        assertThrows(BadRequestException.class, () -> service.getRefereeCheckedInCount(userId));
        assertThrows(BadRequestException.class, () -> service.getRefereePendingCheckInCount(userId));
    }

    @Test
    void revenueReturnsEveryRequestedMonthIncludingZeroMonths() {
        YearMonth current = YearMonth.now();
        YearMonth first = current.minusMonths(2);
        when(walletTransactionRepository.sumAdminRevenueByMonth(any(), any(), any(), any(), any()))
                .thenReturn(List.<Object[]>of(
                        new Object[]{first.getYear(), first.getMonthValue(), new BigDecimal("120000000")},
                        new Object[]{current.getYear(), current.getMonthValue(), new BigDecimal("410000000")}));

        List<AdminDashboardRevenueResponse> response = service.getAdminDashboardRevenue(3);

        assertEquals(3, response.size());
        assertEquals(new BigDecimal("120000000"), response.get(0).getAmount());
        assertEquals(BigDecimal.ZERO, response.get(1).getAmount());
        assertEquals(new BigDecimal("410000000"), response.get(2).getAmount());
        assertNull(response.get(2).getGrowthPercent());
        assertEquals(current.getYear(), response.get(2).getYear());
        assertEquals(current.getMonthValue(), response.get(2).getMonth());
    }

    @Test
    void summaryKeepsLegacyFieldsAndAddsMonthlyGrowthMetrics() {
        when(tournamentRepository.countByStatusNot(any())).thenReturn(4L);
        when(raceRepository.countActiveForAdminDashboard()).thenReturn(23L);
        when(raceRegistrationRepository.countValidForAdminDashboard(any())).thenReturn(99L);
        when(userRepository.countActiveExcludingRole(any())).thenReturn(160L);
        when(walletTransactionRepository.sumAdminRevenue(any(), any(), any()))
                .thenReturn(new BigDecimal("2400000000"));
        when(tournamentRepository.countActiveCreatedBetween(any(), any())).thenReturn(12L, 10L);
        when(raceRepository.countActiveCreatedBetween(any(), any())).thenReturn(8L, 10L);
        when(userRepository.countActiveExcludingRoleCreatedBetween(any(), any(), any())).thenReturn(0L, 0L);
        when(walletTransactionRepository.sumAdminRevenueBetween(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("118"), new BigDecimal("100"));

        AdminDashboardSummaryResponse response = service.getAdminDashboardSummary();

        assertEquals(4L, response.getTournamentCount());
        assertEquals(23L, response.getRaceCount());
        assertEquals(99L, response.getRegistrationCount());
        assertEquals(new BigDecimal("2400000000"), response.getRevenue());
        assertEquals(new BigDecimal("4"), response.getTournament().getValue());
        assertEquals(new BigDecimal("20.00"), response.getTournament().getGrowthPercent());
        assertEquals(new BigDecimal("-20.00"), response.getRace().getGrowthPercent());
        assertEquals(BigDecimal.ZERO, response.getActiveUser().getGrowthPercent());
        assertEquals(new BigDecimal("18.00"), response.getRevenueMetric().getGrowthPercent());
    }

    @Test
    void topHorsesUseCompetitionRankingForEqualStatistics() {
        when(raceResultRepository.findTopHorseStatistics(any(Pageable.class)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{1L, "Thunder Bolt", 11L, "Owner A", 4L, new BigDecimal("850")},
                        new Object[]{2L, "Black Pearl", 12L, "Owner B", 4L, new BigDecimal("850")},
                        new Object[]{3L, "Wind Runner", 13L, "Owner C", 3L, new BigDecimal("900")}));

        List<AdminDashboardTopHorseResponse> response = service.getAdminTopHorses(3);

        assertEquals(List.of(1, 1, 3), response.stream().map(AdminDashboardTopHorseResponse::getRank).toList());
    }

    @Test
    void invalidQueryLimitsAreRejected() {
        assertThrows(BadRequestException.class, () -> service.getAdminDashboardRevenue(0));
        assertThrows(BadRequestException.class, () -> service.getAdminDashboardRevenue(25));
        assertThrows(BadRequestException.class, () -> service.getAdminTopHorses(0));
        assertThrows(BadRequestException.class, () -> service.getAdminTopHorses(21));
        assertThrows(BadRequestException.class, () -> service.getAdminTournamentRaceCounts(0));
        assertThrows(BadRequestException.class, () -> service.getAdminTournamentRaceCounts(51));
        assertThrows(BadRequestException.class, () -> service.getAdminFeaturedTournaments(0));
        assertThrows(BadRequestException.class, () -> service.getAdminFeaturedTournaments(11));
    }

    @Test
    void tournamentRaceCountsAreSortedAndUseInitials() {
        when(tournamentRepository.summarizeRegistrationCapacity()).thenReturn(List.<Object[]>of(
                new Object[]{2L, "Saigon Derby 2026", 4L, 20L},
                new Object[]{1L, "Vietnam Grand Prix 2026", 6L, 30L},
                new Object[]{3L, "Hanoi Cup", 6L, 30L}));

        List<AdminDashboardTournamentRaceCountResponse> response =
                service.getAdminTournamentRaceCounts(2);

        assertEquals(List.of(1L, 3L), response.stream()
                .map(AdminDashboardTournamentRaceCountResponse::getTournamentId).toList());
        assertEquals("VGP", response.get(0).getShortName());
        assertEquals("HC", response.get(1).getShortName());
    }

    @Test
    void featuredTournamentsSortByRegistrationsThenNearestStart() {
        LocalDateTime now = LocalDateTime.now();
        when(raceRegistrationRepository.countValidByTournament(any())).thenReturn(List.<Object[]>of(
                new Object[]{1L, "Vietnam GP", 42L},
                new Object[]{2L, "Saigon Derby", 42L}));
        when(tournamentRepository.summarizeFeaturedCandidates()).thenReturn(List.<Object[]>of(
                new Object[]{1L, "Vietnam GP", "vietnam.jpg", now.plusDays(10),
                        TournamentStatus.SCHEDULED, 6L},
                new Object[]{2L, "Saigon Derby", "saigon.jpg", now.plusDays(2),
                        TournamentStatus.OPEN_REGISTRATION, 5L},
                new Object[]{3L, "Hanoi Cup", "hanoi.jpg", now.plusDays(1),
                        TournamentStatus.PUBLISHED, 8L}));

        List<AdminDashboardFeaturedTournamentResponse> response =
                service.getAdminFeaturedTournaments(3);

        assertEquals(List.of(2L, 1L, 3L), response.stream()
                .map(AdminDashboardFeaturedTournamentResponse::getTournamentId).toList());
        assertEquals(0L, response.get(2).getRegistrationCount());
        assertEquals("saigon.jpg", response.get(0).getBannerUrl());
    }

    @Test
    void quickInsightsAreEmptyWhenThereIsNoUsableData() {
        when(tournamentRepository.summarizeRegistrationCapacity()).thenReturn(List.of());
        when(raceRegistrationRepository.countValidByTournament(any())).thenReturn(List.of());
        when(raceResultRepository.sumFinalizedPrizeAmount()).thenReturn(BigDecimal.ZERO);
        when(raceRegistrationRepository.countValidByMonth(any(), any(), any())).thenReturn(List.of());

        List<AdminDashboardInsightResponse> response = service.getAdminQuickInsights(6);

        assertTrue(response.isEmpty());
    }
}
