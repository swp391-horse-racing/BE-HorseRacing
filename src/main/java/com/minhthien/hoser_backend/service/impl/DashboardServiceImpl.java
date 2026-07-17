package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.*;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.DashboardService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private static final int RECENT_LIMIT = 10;
    private static final PageRequest RECENT_PAGE = PageRequest.of(0, RECENT_LIMIT);
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<RaceRegistrationStatus> VALID_REGISTRATION_STATUSES =
            EnumSet.of(RaceRegistrationStatus.PENDING, RaceRegistrationStatus.APPROVED);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final NotificationRepository notificationRepository;
    private final HorseRepository horseRepository;
    private final RaceRegistrationRepository raceRegistrationRepository;
    private final JockeyInvitationRepository jockeyInvitationRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final TournamentRepository tournamentRepository;
    private final RaceRepository raceRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceResultRepository raceResultRepository;
    private final BetRepository betRepository;
    private final BetMarketRepository betMarketRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentCallbackLogRepository paymentCallbackLogRepository;
    private final AdminWalletWithdrawalRepository adminWalletWithdrawalRepository;
    private final RaceComplaintRepository raceComplaintRepository;
    private final WalletService walletService;
    private final FinanceSettingsService financeSettingsService;

    @Override
    @Transactional
    public DashboardResponse getCurrentUserDashboard(Long userId) {
        User user = requireUser(userId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("roleApprovalStatus", user.getRoleApprovalStatus());
        summary.put("pendingRole", user.getPendingRole());
        summary.put("roleReviewReason", user.getRoleReviewReason());
        return buildDashboard(user, summary, List.of(), userQuickLinks(), List.of());
    }

    @Override
    @Transactional
    public DashboardResponse getOwnerDashboard(Long userId) {
        User user = requireRole(userId, UserRole.OWNER);
        List<Horse> horses = horseRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        List<RaceRegistration> registrations = raceRegistrationRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        List<JockeyInvitation> invitations = jockeyInvitationRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        List<RaceResponse> races = ownerRaceResponses(userId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("horseCount", horses.size());
        summary.put("horsesByStatus", countBy(horses, horse -> horse.getStatus().name()));
        summary.put("registrationsByStatus", countBy(registrations, registration -> registration.getStatus().name()));
        summary.put("jockeyInvitationsByStatus", countBy(invitations, invitation -> invitation.getStatus().name()));
        summary.put("acceptedJockeyCount", invitations.stream()
                .filter(invitation -> invitation.getStatus() == AssignmentStatus.ACCEPTED)
                .map(invitation -> invitation.getJockey().getId())
                .distinct()
                .count());
        summary.put("upcomingRaceCount", upcomingRaceItems(races).size());
        summary.put("openTournamentCount", tournamentRepository.countByStatus(TournamentStatus.OPEN_REGISTRATION));

        List<DashboardItemResponse> alerts = new ArrayList<>();
        addAlert(alerts, "HORSE_PENDING", "Horses pending review",
                countStatus(horses, horse -> horse.getStatus().name(), HorseStatus.PENDING.name()));
        addAlert(alerts, "REGISTRATION_PENDING", "Race registrations pending review",
                countStatus(registrations, registration -> registration.getStatus().name(), RaceRegistrationStatus.PENDING.name()));
        addAlert(alerts, "JOCKEY_INVITATION_PENDING", "Jockey invitations waiting for response",
                countStatus(invitations, invitation -> invitation.getStatus().name(), AssignmentStatus.PENDING.name()));

        return buildDashboard(user, summary, alerts, ownerQuickLinks(), upcomingRaceItems(races));
    }

    @Override
    @Transactional
    public DashboardResponse getJockeyDashboard(Long userId) {
        User user = requireRole(userId, UserRole.JOCKEY);
        Optional<JockeyProfile> profile = jockeyProfileRepository.findByUserId(userId);
        List<JockeyInvitation> invitations = jockeyInvitationRepository.findByJockeyIdOrderByCreatedAtDesc(userId);
        JockeyPerformanceResponse performance = buildJockeyPerformance(userId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("profileStatus", profile.map(p -> p.getStatus().name()).orElse("MISSING"));
        summary.put("invitationsByStatus", countBy(invitations, invitation -> invitation.getStatus().name()));
        summary.put("raceCount", performance.getRaceCount());
        summary.put("completedRaceCount", performance.getCompletedRaceCount());
        summary.put("firstPlaces", performance.getFirstPlaces());
        summary.put("secondPlaces", performance.getSecondPlaces());
        summary.put("thirdPlaces", performance.getThirdPlaces());
        summary.put("totalJockeyPayout", performance.getTotalJockeyPayout());
        summary.put("totalPrizePayout", performance.getTotalPrizePayout());

        List<DashboardItemResponse> alerts = new ArrayList<>();
        if (profile.isEmpty()) {
            alerts.add(item("JOCKEY_PROFILE", null, "Jockey profile is missing", "MISSING", null));
        }
        addAlert(alerts, "JOCKEY_INVITATION_PENDING", "New invitations waiting for response",
                countStatus(invitations, invitation -> invitation.getStatus().name(), AssignmentStatus.PENDING.name()));

        return buildDashboard(user, summary, alerts, jockeyQuickLinks(), upcomingRaceItems(performance.getRecentRaces()));
    }

    @Override
    @Transactional
    public DashboardResponse getRefereeDashboard(Long userId) {
        User user = requireRole(userId, UserRole.REFEREE);
        List<RaceResponse> races = raceRepository.findByRefereeIdOrderByScheduledStartAtAsc(userId).stream()
                .map(this::mapRace)
                .toList();
        LocalDate today = LocalDate.now();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("racesByStatus", countBy(races, race -> race.getStatus().name()));
        summary.put("todayRaceCount", races.stream().filter(race -> isSameDay(race.getScheduledStartAt(), today)).count());
        summary.put("upcomingRaceCount", upcomingRaceItems(races).size());
        summary.put("checkInRaceCount", races.stream().filter(race -> race.getStatus() == RaceStatus.SCHEDULED).count());
        summary.put("resultEntryRaceCount", races.stream().filter(race -> race.getStatus() == RaceStatus.ONGOING).count());

        List<DashboardItemResponse> alerts = new ArrayList<>();
        addAlert(alerts, "REFEREE_CHECK_IN", "Races ready for check-in",
                races.stream().filter(race -> race.getStatus() == RaceStatus.SCHEDULED).count());
        addAlert(alerts, "REFEREE_RESULT_ENTRY", "Races waiting for result entry",
                races.stream().filter(race -> race.getStatus() == RaceStatus.ONGOING).count());

        return buildDashboard(user, summary, alerts, refereeQuickLinks(), upcomingRaceItems(races));
    }

    @Override
    @Transactional(readOnly = true)
    public RefereeCheckInCountResponse getRefereeCheckedInCount(Long userId) {
        requireRole(userId, UserRole.REFEREE);
        long count = raceParticipantRepository.countByRaceRefereeIdAndStatus(
                userId, RaceParticipantStatus.CHECKED_IN);
        return new RefereeCheckInCountResponse(count);
    }

    @Override
    @Transactional(readOnly = true)
    public RefereeCheckInCountResponse getRefereePendingCheckInCount(Long userId) {
        requireRole(userId, UserRole.REFEREE);
        long count = raceParticipantRepository.countByRaceRefereeIdAndStatus(
                userId, RaceParticipantStatus.REGISTERED);
        return new RefereeCheckInCountResponse(count);
    }

    @Override
    @Transactional
    public DashboardResponse getSpectatorDashboard(Long userId) {
        User user = requireRole(userId, UserRole.SPECTATOR);
        List<BetMarket> openMarkets = betMarketRepository.findByStatusOrderByRaceScheduledStartAtAsc(
                BetMarketStatus.OPEN, RECENT_PAGE);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openTournamentCount", tournamentRepository.countByStatus(TournamentStatus.OPEN_REGISTRATION));
        summary.put("openBetMarketCount", betMarketRepository.countByStatus(BetMarketStatus.OPEN));
        summary.put("betsByStatus", countRowsByEnumName(betRepository.countByStatusGroupForUser(userId)));
        summary.put("totalBetStake", zero(betRepository.sumStakeAmountByUserId(userId)));
        summary.put("totalBetPayout", zero(betRepository.sumNetProfitAmountByUserId(userId)));
        summary.put("bettingEnabled", financeSettingsService.isBettingEnabled());
        summary.put("predictionEnabled", false);
        summary.put("marketplaceEnabled", false);

        List<DashboardItemResponse> upcoming = openMarkets.stream()
                .limit(RECENT_LIMIT)
                .map(market -> item("BET_MARKET", market.getId(), market.getRace().getName(),
                        market.getStatus().name(), market.getRace().getScheduledStartAt()))
                .toList();

        return buildDashboard(user, summary, List.of(), spectatorQuickLinks(), upcoming);
    }

    @Override
    @Transactional
    public DashboardResponse getAdminDashboard(Long userId) {
        User user = requireRole(userId, UserRole.ADMIN);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        List<Race> upcomingRaces = raceRepository.findByScheduledStartAtGreaterThanEqualOrderByScheduledStartAtAsc(
                LocalDateTime.now(), RECENT_PAGE);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("usersByRole", countRowsByEnumName(userRepository.countByRoleGroup()));
        summary.put("activeUserCount", userRepository.countByActive(true));
        summary.put("deactivatedUserCount", userRepository.countByActive(false));
        summary.put("pendingRoleApplicationCount", userRepository.countByRoleApprovalStatus(RoleApprovalStatus.PENDING));
        summary.put("pendingHorseCount", horseRepository.countByStatus(HorseStatus.PENDING));
        summary.put("pendingJockeyProfileCount", jockeyProfileRepository.countByStatus(JockeyStatus.PENDING));
        summary.put("openTournamentCount", tournamentRepository.countByStatus(TournamentStatus.OPEN_REGISTRATION));
        summary.put("ongoingTournamentCount", tournamentRepository.countByStatus(TournamentStatus.ONGOING));
        summary.put("pendingWithdrawalCount", withdrawalRequestRepository.countByStatus(WithdrawalStatus.PENDING));
        summary.put("todayRaceCount", raceRepository.countByScheduledStartAtBetween(todayStart, tomorrowStart));
        summary.put("pendingComplaintCount", raceComplaintRepository.countByStatus(RaceComplaintStatus.PENDING));
        summary.put("paymentOrdersByStatus", countRowsByEnumName(paymentOrderRepository.countByStatusGroup()));
        summary.put("paymentCallbackLogCount", paymentCallbackLogRepository.count());
        summary.put("adminWalletWithdrawalCount", adminWalletWithdrawalRepository.count());

        List<DashboardItemResponse> alerts = new ArrayList<>();
        addAlert(alerts, "ROLE_APPLICATION_PENDING", "Role applications pending review",
                (Long) summary.get("pendingRoleApplicationCount"));
        addAlert(alerts, "HORSE_PENDING", "Horses pending review", (Long) summary.get("pendingHorseCount"));
        addAlert(alerts, "JOCKEY_PROFILE_PENDING", "Jockey profiles pending review",
                (Long) summary.get("pendingJockeyProfileCount"));
        addAlert(alerts, "WITHDRAWAL_PENDING", "Withdrawals pending review", (Long) summary.get("pendingWithdrawalCount"));

        return buildAdminDashboard(user, summary, alerts, adminQuickLinks(), adminUpcomingRaces(upcomingRaces));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getOwnerRaces(Long userId) {
        requireRole(userId, UserRole.OWNER);
        return ownerRaceResponses(userId);
    }

    @Override
    @Transactional
    public List<WalletTransactionResponse> getOwnerPrizes(Long userId) {
        requireRole(userId, UserRole.OWNER);
        return userTransactions(userId).stream()
                .filter(transaction -> transaction.getType() == WalletTransactionType.PRIZE_PAYOUT)
                .map(this::mapTransaction)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getJockeyRaces(Long userId) {
        requireRole(userId, UserRole.JOCKEY);
        return jockeyRaceResponses(userId);
    }

    @Override
    @Transactional
    public JockeyPerformanceResponse getJockeyPerformance(Long userId) {
        requireRole(userId, UserRole.JOCKEY);
        return buildJockeyPerformance(userId);
    }

    @Override
    @Transactional
    public List<WalletTransactionResponse> getJockeyPrizes(Long userId) {
        requireRole(userId, UserRole.JOCKEY);
        return userTransactions(userId).stream()
                .filter(transaction -> transaction.getType() == WalletTransactionType.PRIZE_PAYOUT
                        || transaction.getType() == WalletTransactionType.JOCKEY_PAYOUT)
                .map(this::mapTransaction)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResponse> getAdminRaces(LocalDateTime from, LocalDateTime to, RaceStatus status) {
        LocalDateTime queryFrom = from == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : from;
        LocalDateTime queryTo = to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to;
        List<Race> races = status == null
                ? raceRepository.findByScheduledStartAtBetweenOrderByScheduledStartAtAsc(queryFrom, queryTo, PageRequest.of(0, 200))
                : raceRepository.findByStatusAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(
                        status, queryFrom, queryTo, PageRequest.of(0, 200));
        return races.stream()
                .map(this::mapRace)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getAdminDashboardSummary() {
        MonthWindow monthWindow = currentMonthWindow();
        long tournamentCount = tournamentRepository.countByStatusNot(TournamentStatus.CANCELLED);
        long raceCount = raceRepository.countActiveForAdminDashboard();
        long registrationCount = raceRegistrationRepository.countValidForAdminDashboard(
                VALID_REGISTRATION_STATUSES);
        long activeUserCount = userRepository.countActiveExcludingRole(UserRole.ADMIN);
        BigDecimal revenue = adminRevenue();

        return AdminDashboardSummaryResponse.builder()
                .tournamentCount(tournamentCount)
                .raceCount(raceCount)
                .registrationCount(registrationCount)
                .revenue(revenue)
                .tournament(metric(tournamentCount,
                        tournamentRepository.countActiveCreatedBetween(
                                monthWindow.currentStart(), monthWindow.nextStart()),
                        tournamentRepository.countActiveCreatedBetween(
                                monthWindow.previousStart(), monthWindow.currentStart())))
                .race(metric(raceCount,
                        raceRepository.countActiveCreatedBetween(
                                monthWindow.currentStart(), monthWindow.nextStart()),
                        raceRepository.countActiveCreatedBetween(
                                monthWindow.previousStart(), monthWindow.currentStart())))
                .activeUser(metric(activeUserCount,
                        userRepository.countActiveExcludingRoleCreatedBetween(
                                UserRole.ADMIN, monthWindow.currentStart(), monthWindow.nextStart()),
                        userRepository.countActiveExcludingRoleCreatedBetween(
                                UserRole.ADMIN, monthWindow.previousStart(), monthWindow.currentStart())))
                .revenueMetric(metric(revenue,
                        adminRevenueBetween(monthWindow.currentStart(), monthWindow.nextStart()),
                        adminRevenueBetween(monthWindow.previousStart(), monthWindow.currentStart())))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDashboardRevenueResponse> getAdminDashboardRevenue(int months) {
        validateRange(months, 1, 24, "months");
        YearMonth currentMonth = YearMonth.now(DASHBOARD_ZONE);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        YearMonth queryFirstMonth = firstMonth.minusMonths(1);
        LocalDateTime from = queryFirstMonth.atDay(1).atStartOfDay();
        LocalDateTime to = currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        Map<YearMonth, BigDecimal> revenueByMonth = walletTransactionRepository
                .sumAdminRevenueByMonth(WalletOwnerType.ADMIN, WalletTransactionDirection.CREDIT,
                        WalletTransactionStatus.SUCCESS, from, to)
                .stream()
                .collect(Collectors.toMap(
                        row -> YearMonth.of(number(row[0]).intValue(), number(row[1]).intValue()),
                        row -> zero((BigDecimal) row[2])));

        List<AdminDashboardRevenueResponse> response = new ArrayList<>();
        for (int offset = 0; offset < months; offset++) {
            YearMonth month = firstMonth.plusMonths(offset);
            BigDecimal amount = revenueByMonth.getOrDefault(month, BigDecimal.ZERO);
            response.add(AdminDashboardRevenueResponse.builder()
                    .year(month.getYear())
                    .month(month.getMonthValue())
                    .label("T" + month.getMonthValue())
                    .amount(amount)
                    .growthPercent(offset == months - 1
                            ? growthPercent(amount,
                            revenueByMonth.getOrDefault(month.minusMonths(1), BigDecimal.ZERO))
                            : null)
                    .build());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDashboardTournamentRegistrationResponse> getAdminTournamentRegistrations() {
        Map<Long, Long> registrationsByTournament = raceRegistrationRepository
                .countValidByTournament(VALID_REGISTRATION_STATUSES)
                .stream()
                .collect(Collectors.toMap(row -> number(row[0]).longValue(), row -> number(row[2]).longValue()));

        return tournamentRepository.summarizeRegistrationCapacity().stream()
                .map(row -> {
                    Long tournamentId = number(row[0]).longValue();
                    long registrationCount = registrationsByTournament.getOrDefault(tournamentId, 0L);
                    long capacity = number(row[3]).longValue();
                    return AdminDashboardTournamentRegistrationResponse.builder()
                            .tournamentId(tournamentId)
                            .tournamentName((String) row[1])
                            .raceCount(number(row[2]).longValue())
                            .registrationCount(registrationCount)
                            .capacity(capacity)
                            .fillRate(percent(registrationCount, capacity))
                            .build();
                })
                .sorted(Comparator.comparing(AdminDashboardTournamentRegistrationResponse::getRegistrationCount)
                        .reversed()
                        .thenComparing(AdminDashboardTournamentRegistrationResponse::getTournamentId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDashboardTopHorseResponse> getAdminTopHorses(int limit) {
        validateRange(limit, 1, 20, "limit");
        List<Object[]> rows = raceResultRepository.findTopHorseStatistics(PageRequest.of(0, limit));
        List<AdminDashboardTopHorseResponse> response = new ArrayList<>();
        Long previousWins = null;
        BigDecimal previousPrize = null;
        int rank = 0;

        for (int index = 0; index < rows.size(); index++) {
            Object[] row = rows.get(index);
            long wins = number(row[4]).longValue();
            BigDecimal prize = zero((BigDecimal) row[5]);
            if (previousWins == null || previousWins != wins || previousPrize.compareTo(prize) != 0) {
                rank = index + 1;
            }
            response.add(AdminDashboardTopHorseResponse.builder()
                    .rank(rank)
                    .horseId(number(row[0]).longValue())
                    .horseName((String) row[1])
                    .ownerId(number(row[2]).longValue())
                    .ownerName((String) row[3])
                    .winCount(wins)
                    .totalPrizeAmount(prize)
                    .build());
            previousWins = wins;
            previousPrize = prize;
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDashboardInsightResponse> getAdminQuickInsights(int months) {
        validateRange(months, 1, 24, "months");
        List<AdminDashboardInsightResponse> insights = new ArrayList<>();
        List<AdminDashboardTournamentRegistrationResponse> tournaments = getAdminTournamentRegistrations();

        long totalCapacity = tournaments.stream()
                .mapToLong(AdminDashboardTournamentRegistrationResponse::getCapacity)
                .sum();
        long totalRegistrations = tournaments.stream()
                .mapToLong(AdminDashboardTournamentRegistrationResponse::getRegistrationCount)
                .sum();
        if (totalCapacity > 0) {
            BigDecimal fillRate = percent(totalRegistrations, totalCapacity);
            insights.add(insight("AVERAGE_FILL_RATE",
                    "Tỷ lệ lấp đầy đăng ký trung bình đạt " + formatPercent(fillRate) + "%.",
                    fillRate, "PERCENT",
                    Map.of("registrationCount", totalRegistrations, "capacity", totalCapacity)));
        }

        tournaments.stream()
                .filter(tournament -> tournament.getRegistrationCount() > 0)
                .findFirst()
                .ifPresent(tournament -> insights.add(insight("TOP_TOURNAMENT_REGISTRATIONS",
                        tournament.getTournamentName() + " có số đăng ký cao nhất.",
                        BigDecimal.valueOf(tournament.getRegistrationCount()), "COUNT",
                        Map.of("tournamentId", tournament.getTournamentId(),
                                "tournamentName", tournament.getTournamentName()))));

        BigDecimal totalPrize = zero(raceResultRepository.sumFinalizedPrizeAmount());
        if (totalPrize.compareTo(BigDecimal.ZERO) > 0) {
            List<AdminDashboardTopHorseResponse> topThreeHorses = getAdminTopHorses(3);
            BigDecimal topThreePrize = topThreeHorses.stream()
                    .map(AdminDashboardTopHorseResponse::getTotalPrizeAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (topThreeHorses.size() == 3 && topThreePrize.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal share = topThreePrize.multiply(BigDecimal.valueOf(100))
                        .divide(totalPrize, 2, RoundingMode.HALF_UP);
                insights.add(insight("TOP_THREE_HORSE_PRIZE_SHARE",
                        "Top 3 ngựa tạo ra " + formatPercent(share) + "% tổng tiền thưởng.",
                        share, "PERCENT", Map.of("topThreePrizeAmount", topThreePrize,
                                "totalPrizeAmount", totalPrize)));
            }
        }

        if (months >= 2) {
            addRegistrationTrendInsight(insights, months);
        }
        return insights.stream().limit(4).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDashboardTournamentRaceCountResponse> getAdminTournamentRaceCounts(int limit) {
        validateRange(limit, 1, 50, "limit");
        return tournamentRepository.summarizeRegistrationCapacity().stream()
                .map(row -> AdminDashboardTournamentRaceCountResponse.builder()
                        .tournamentId(number(row[0]).longValue())
                        .tournamentName((String) row[1])
                        .shortName(shortName((String) row[1]))
                        .raceCount(number(row[2]).longValue())
                        .build())
                .sorted(Comparator.comparing(AdminDashboardTournamentRaceCountResponse::getRaceCount)
                        .reversed()
                        .thenComparing(AdminDashboardTournamentRaceCountResponse::getTournamentId))
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminDashboardFeaturedTournamentResponse> getAdminFeaturedTournaments(int limit) {
        validateRange(limit, 1, 10, "limit");
        Map<Long, Long> registrationsByTournament = raceRegistrationRepository
                .countValidByTournament(VALID_REGISTRATION_STATUSES)
                .stream()
                .collect(Collectors.toMap(row -> number(row[0]).longValue(), row -> number(row[2]).longValue()));
        LocalDateTime now = LocalDateTime.now(DASHBOARD_ZONE);

        return tournamentRepository.summarizeFeaturedCandidates().stream()
                .map(row -> {
                    Long tournamentId = number(row[0]).longValue();
                    return AdminDashboardFeaturedTournamentResponse.builder()
                            .tournamentId(tournamentId)
                            .name((String) row[1])
                            .bannerUrl((String) row[2])
                            .startAt((LocalDateTime) row[3])
                            .status((TournamentStatus) row[4])
                            .raceCount(number(row[5]).longValue())
                            .registrationCount(registrationsByTournament.getOrDefault(tournamentId, 0L))
                            .build();
                })
                .sorted(Comparator.comparing(AdminDashboardFeaturedTournamentResponse::getRegistrationCount)
                        .reversed()
                        .thenComparing(tournament -> distanceFrom(now, tournament.getStartAt()))
                        .thenComparing(AdminDashboardFeaturedTournamentResponse::getTournamentId))
                .limit(limit)
                .toList();
    }

    private DashboardResponse buildDashboard(User user,
                                             Map<String, Object> businessSummary,
                                             List<DashboardItemResponse> alerts,
                                             List<DashboardQuickLinkResponse> quickLinks,
                                             List<DashboardItemResponse> upcoming) {
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletService.getOrCreateUserWallet(user.getId()));
        List<WalletTransaction> transactions = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(
                wallet.getId(), RECENT_PAGE);
        return DashboardResponse.builder()
                .role(user.getRole())
                .account(account(user))
                .wallet(mapWallet(wallet))
                .moneyIn(sumTransactions(wallet.getId(), List.of(WalletTransactionDirection.CREDIT)))
                .moneyOut(sumTransactions(wallet.getId(), List.of(WalletTransactionDirection.DEBIT, WalletTransactionDirection.CAPTURE)))
                .hold(sumTransactions(wallet.getId(), List.of(WalletTransactionDirection.HOLD)))
                .withdrawals(withdrawalSummaryFromRows(withdrawalRepositoryRowsForUser(user.getId())))
                .recentTransactions(transactions.stream().limit(RECENT_LIMIT).map(this::mapTransaction).toList())
                .recentNotifications(recentNotifications(user.getId()))
                .businessSummary(businessSummary)
                .alerts(alerts)
                .upcoming(upcoming)
                .quickLinks(quickLinks)
                .featureFlags(featureFlags())
                .build();
    }

    private DashboardResponse buildAdminDashboard(User user,
                                                  Map<String, Object> businessSummary,
                                                  List<DashboardItemResponse> alerts,
                                                  List<DashboardQuickLinkResponse> quickLinks,
                                                  List<DashboardItemResponse> upcoming) {
        Wallet wallet = walletService.getOrCreateAdminWallet();
        List<WalletTransaction> transactions = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(
                wallet.getId(), RECENT_PAGE);
        return DashboardResponse.builder()
                .role(user.getRole())
                .account(account(user))
                .wallet(mapWallet(wallet))
                .moneyIn(sumTransactions(wallet.getId(), List.of(WalletTransactionDirection.CREDIT)))
                .moneyOut(sumTransactions(wallet.getId(), List.of(WalletTransactionDirection.DEBIT, WalletTransactionDirection.CAPTURE)))
                .hold(sumTransactions(wallet.getId(), List.of(WalletTransactionDirection.HOLD)))
                .withdrawals(withdrawalSummaryFromRows(withdrawalRepositoryRows()))
                .recentTransactions(transactions.stream().limit(RECENT_LIMIT).map(this::mapTransaction).toList())
                .recentNotifications(recentNotifications(user.getId()))
                .businessSummary(businessSummary)
                .alerts(alerts)
                .upcoming(upcoming)
                .quickLinks(quickLinks)
                .featureFlags(featureFlags())
                .build();
    }

    private List<WalletTransaction> userTransactions(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> walletService.getOrCreateUserWallet(userId));
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
    }

    private List<RaceResponse> ownerRaceResponses(Long ownerId) {
        return raceRegistrationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(RaceRegistration::getRace)
                .collect(Collectors.toMap(Race::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(Race::getScheduledStartAt))
                .map(this::mapRace)
                .toList();
    }

    private List<RaceResponse> jockeyRaceResponses(Long jockeyId) {
        return raceRegistrationRepository.findByJockeyIdOrderByCreatedAtDesc(jockeyId).stream()
                .map(RaceRegistration::getRace)
                .collect(Collectors.toMap(Race::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(Race::getScheduledStartAt))
                .map(this::mapRace)
                .toList();
    }

    private JockeyPerformanceResponse buildJockeyPerformance(Long jockeyId) {
        List<RaceResponse> races = jockeyRaceResponses(jockeyId);
        return JockeyPerformanceResponse.builder()
                .jockeyId(jockeyId)
                .raceCount((long) races.size())
                .completedRaceCount(raceResultRepository.countCompletedByJockeyId(jockeyId))
                .firstPlaces(raceResultRepository.countByJockeyIdAndRank(jockeyId, 1))
                .secondPlaces(raceResultRepository.countByJockeyIdAndRank(jockeyId, 2))
                .thirdPlaces(raceResultRepository.countByJockeyIdAndRank(jockeyId, 3))
                .totalJockeyPayout(zero(walletTransactionRepository.sumJockeyPayoutByUserId(jockeyId)))
                .totalPrizePayout(zero(walletTransactionRepository.sumPrizePayoutByUserId(jockeyId)))
                .recentRaces(races.stream().limit(RECENT_LIMIT).toList())
                .build();
    }

    private List<NotificationResponse> recentNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, RECENT_LIMIT))
                .getContent()
                .stream()
                .map(this::mapNotification)
                .toList();
    }

    private List<DashboardItemResponse> upcomingRaceItems(List<RaceResponse> races) {
        LocalDateTime now = LocalDateTime.now();
        return races.stream()
                .filter(race -> race.getScheduledStartAt() != null && !race.getScheduledStartAt().isBefore(now))
                .sorted(Comparator.comparing(RaceResponse::getScheduledStartAt))
                .limit(RECENT_LIMIT)
                .map(race -> item("RACE", race.getId(), race.getName(), race.getStatus().name(), race.getScheduledStartAt()))
                .toList();
    }

    private List<DashboardItemResponse> adminUpcomingRaces(List<Race> races) {
        LocalDateTime now = LocalDateTime.now();
        return races.stream()
                .filter(race -> race.getScheduledStartAt() != null && !race.getScheduledStartAt().isBefore(now))
                .sorted(Comparator.comparing(Race::getScheduledStartAt))
                .limit(RECENT_LIMIT)
                .map(race -> item("RACE", race.getId(), race.getName(), race.getStatus().name(), race.getScheduledStartAt()))
                .toList();
    }

    private DashboardResponse.WithdrawalSummary withdrawalSummary(List<WithdrawalRequest> withdrawals) {
        return DashboardResponse.WithdrawalSummary.builder()
                .total((long) withdrawals.size())
                .countByStatus(countBy(withdrawals, withdrawal -> withdrawal.getStatus().name()))
                .amountByStatus(sumBy(withdrawals, withdrawal -> withdrawal.getStatus().name(), WithdrawalRequest::getAmount))
                .build();
    }

    private DashboardResponse.WithdrawalSummary withdrawalSummaryFromRows(List<Object[]> rows) {
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        Map<String, BigDecimal> amountByStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : rows) {
            String status = ((Enum<?>) row[0]).name();
            long count = ((Number) row[1]).longValue();
            countByStatus.put(status, count);
            amountByStatus.put(status, zero((BigDecimal) row[2]));
            total += count;
        }
        return DashboardResponse.WithdrawalSummary.builder()
                .total(total)
                .countByStatus(countByStatus)
                .amountByStatus(amountByStatus)
                .build();
    }

    private Map<String, BigDecimal> sumTransactions(List<WalletTransaction> transactions,
                                                    Set<WalletTransactionDirection> directions) {
        return sumBy(transactions.stream()
                        .filter(transaction -> directions.contains(transaction.getDirection()))
                        .toList(),
                transaction -> transaction.getType().name(),
                WalletTransaction::getAmount);
    }

    private Map<String, BigDecimal> sumTransactions(Long walletId, List<WalletTransactionDirection> directions) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        walletTransactionRepository.sumAmountByTypeForWalletAndDirection(walletId, directions)
                .forEach(row -> totals.put(((Enum<?>) row[0]).name(), zero((BigDecimal) row[1])));
        return totals;
    }

    private List<Object[]> withdrawalRepositoryRowsForUser(Long userId) {
        return withdrawalRequestRepository.summarizeByStatusForUser(userId);
    }

    private List<Object[]> withdrawalRepositoryRows() {
        return withdrawalRequestRepository.summarizeByStatus();
    }

    private Map<String, Long> countRowsByEnumName(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        rows.forEach(row -> counts.put(((Enum<?>) row[0]).name(), ((Number) row[1]).longValue()));
        return counts;
    }

    private BigDecimal adminRevenue() {
        return zero(walletTransactionRepository.sumAdminRevenue(
                WalletOwnerType.ADMIN,
                WalletTransactionDirection.CREDIT,
                WalletTransactionStatus.SUCCESS));
    }

    private BigDecimal adminRevenueBetween(LocalDateTime from, LocalDateTime to) {
        return zero(walletTransactionRepository.sumAdminRevenueBetween(
                WalletOwnerType.ADMIN,
                WalletTransactionDirection.CREDIT,
                WalletTransactionStatus.SUCCESS,
                from,
                to));
    }

    private AdminDashboardMetricResponse metric(long total, long current, long previous) {
        return metric(BigDecimal.valueOf(total), BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private AdminDashboardMetricResponse metric(BigDecimal total, BigDecimal current, BigDecimal previous) {
        return AdminDashboardMetricResponse.builder()
                .value(zero(total))
                .growthPercent(growthPercent(zero(current), zero(previous)))
                .build();
    }

    private BigDecimal growthPercent(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private MonthWindow currentMonthWindow() {
        YearMonth current = YearMonth.now(DASHBOARD_ZONE);
        return new MonthWindow(
                current.minusMonths(1).atDay(1).atStartOfDay(),
                current.atDay(1).atStartOfDay(),
                current.plusMonths(1).atDay(1).atStartOfDay());
    }

    private String shortName(String tournamentName) {
        if (tournamentName == null || tournamentName.isBlank()) {
            return "";
        }
        String initials = Arrays.stream(tournamentName.trim().split("\\s+"))
                .filter(word -> !word.isBlank() && Character.isLetter(word.codePointAt(0)))
                .map(word -> new String(Character.toChars(word.codePointAt(0))))
                .collect(Collectors.joining())
                .toUpperCase(Locale.ROOT);
        if (initials.isEmpty()) {
            return tournamentName.substring(0, Math.min(5, tournamentName.length())).toUpperCase(Locale.ROOT);
        }
        return initials.substring(0, Math.min(5, initials.length()));
    }

    private long distanceFrom(LocalDateTime now, LocalDateTime target) {
        return target == null ? Long.MAX_VALUE : Math.abs(Duration.between(now, target).toMinutes());
    }

    private void addRegistrationTrendInsight(List<AdminDashboardInsightResponse> insights, int months) {
        YearMonth currentMonth = YearMonth.now(DASHBOARD_ZONE);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        Map<YearMonth, Long> registrationsByMonth = raceRegistrationRepository
                .countValidByMonth(VALID_REGISTRATION_STATUSES,
                        firstMonth.atDay(1).atStartOfDay(),
                        currentMonth.plusMonths(1).atDay(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(
                        row -> YearMonth.of(number(row[0]).intValue(), number(row[1]).intValue()),
                        row -> number(row[2]).longValue()));

        YearMonth previousMonth = currentMonth.minusMonths(1);
        long currentCount = registrationsByMonth.getOrDefault(currentMonth, 0L);
        long previousCount = registrationsByMonth.getOrDefault(previousMonth, 0L);
        if (currentCount == 0 && previousCount == 0) {
            return;
        }

        long difference = currentCount - previousCount;
        String direction = difference > 0 ? "tăng" : "giảm";
        String message = difference == 0
                ? "Số đăng ký tháng này không đổi so với tháng trước."
                : "Số đăng ký tháng này " + direction + " " + Math.abs(difference)
                + " so với tháng trước.";
        insights.add(insight("MONTHLY_REGISTRATION_TREND", message,
                BigDecimal.valueOf(difference), "COUNT_CHANGE",
                Map.of("currentMonth", currentMonth.toString(),
                        "currentCount", currentCount,
                        "previousMonth", previousMonth.toString(),
                        "previousCount", previousCount)));
    }

    private AdminDashboardInsightResponse insight(String code, String message, BigDecimal value,
                                                  String unit, Map<String, Object> metadata) {
        return AdminDashboardInsightResponse.builder()
                .code(code)
                .message(message)
                .value(value)
                .unit(unit)
                .metadata(metadata)
                .build();
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String formatPercent(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private Number number(Object value) {
        return (Number) value;
    }

    private void validateRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max);
        }
    }

    private record MonthWindow(LocalDateTime previousStart,
                               LocalDateTime currentStart,
                               LocalDateTime nextStart) {
    }

    private <T> Map<String, Long> countBy(List<T> items, Function<T, String> mapper) {
        return items.stream().collect(Collectors.groupingBy(mapper, LinkedHashMap::new, Collectors.counting()));
    }

    private <T> Map<String, BigDecimal> sumBy(List<T> items, Function<T, String> keyMapper,
                                              Function<T, BigDecimal> amountMapper) {
        return items.stream().collect(Collectors.groupingBy(keyMapper, LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, item -> zero(amountMapper.apply(item)), BigDecimal::add)));
    }

    private <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> amountMapper) {
        return items.stream().map(amountMapper).map(this::zero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private <T> long countStatus(List<T> items, Function<T, String> mapper, String status) {
        return items.stream().filter(item -> status.equals(mapper.apply(item))).count();
    }

    private void addAlert(List<DashboardItemResponse> alerts, String type, String title, long count) {
        if (count > 0) {
            alerts.add(item(type, null, title, String.valueOf(count), null));
        }
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isSameDay(LocalDateTime at, LocalDate day) {
        return at != null && at.toLocalDate().equals(day);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private User requireRole(Long userId, UserRole role) {
        User user = requireUser(userId);
        if (user.getRole() != role) {
            throw new BadRequestException("Dashboard requires " + role + " role");
        }
        return user;
    }

    private Map<String, Object> account(User user) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", user.getId());
        account.put("username", user.getUsername());
        account.put("email", user.getEmail());
        account.put("fullName", user.getFullName());
        account.put("role", user.getRole());
        account.put("pendingRole", user.getPendingRole());
        account.put("roleApprovalStatus", user.getRoleApprovalStatus());
        account.put("active", user.getActive());
        return account;
    }

    private WalletResponse mapWallet(Wallet wallet) {
        Long userId = wallet.getUser() == null ? null : wallet.getUser().getId();
        return WalletResponse.builder()
                .id(wallet.getId())
                .ownerType(wallet.getOwnerType())
                .userId(userId)
                .currency(wallet.getCurrency())
                .availableBalance(wallet.getAvailableBalance())
                .holdBalance(wallet.getHoldBalance())
                .totalBalance(wallet.getAvailableBalance().add(wallet.getHoldBalance()))
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private WalletTransactionResponse mapTransaction(WalletTransaction transaction) {
        Long userId = transaction.getUser() == null ? null : transaction.getUser().getId();
        return WalletTransactionResponse.builder()
                .id(transaction.getId())
                .walletId(transaction.getWallet().getId())
                .userId(userId)
                .type(transaction.getType())
                .direction(transaction.getDirection())
                .amount(transaction.getAmount())
                .availableBefore(transaction.getAvailableBefore())
                .availableAfter(transaction.getAvailableAfter())
                .holdBefore(transaction.getHoldBefore())
                .holdAfter(transaction.getHoldAfter())
                .status(transaction.getStatus())
                .referenceType(transaction.getReferenceType())
                .referenceId(transaction.getReferenceId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .metadata(transaction.getMetadata())
                .note(transaction.getNote())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private NotificationResponse mapNotification(Notification notification) {
        User recipient = notification.getRecipient();
        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientId(recipient.getId())
                .recipientUsername(recipient.getUsername())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .metadataJson(notification.getMetadataJson())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    private RaceResponse mapRace(Race race) {
        return RaceResponse.builder()
                .id(race.getId())
                .tournamentId(race.getTournament().getId())
                .name(race.getName())
                .distance(race.getDistance())
                .scheduledStartAt(race.getScheduledStartAt())
                .scheduledEndAt(race.getScheduledEndAt())
                .minParticipants(race.getMinParticipants())
                .maxParticipants(race.getMaxParticipants())
                .entryFee(race.getEntryFee())
                .refereeId(race.getReferee() == null ? null : race.getReferee().getId())
                .refereeUsername(race.getReferee() == null ? null : race.getReferee().getUsername())
                .status(race.getStatus())
                .note(race.getNote())
                .resultFinalizedAt(race.getResultFinalizedAt())
                .resultFinalizedBy(race.getResultFinalizedBy())
                .participantCount(race.getParticipants() == null ? 0 : race.getParticipants().size())
                .createdAt(race.getCreatedAt())
                .updatedAt(race.getUpdatedAt())
                .build();
    }

    private DashboardItemResponse item(String type, Long id, String title, String status, LocalDateTime at) {
        return DashboardItemResponse.builder()
                .type(type)
                .id(id)
                .title(title)
                .status(status)
                .at(at)
                .metadata(Map.of())
                .build();
    }

    private Map<String, Boolean> featureFlags() {
        Map<String, Boolean> flags = new LinkedHashMap<>();
        flags.put("betting", financeSettingsService.isBettingEnabled());
        flags.put("prediction", false);
        flags.put("marketplace", false);
        flags.put("refereeReports", false);
        return flags;
    }

    private List<DashboardQuickLinkResponse> userQuickLinks() {
        return links("Choose Role", "Profile", "Wallet", "Notifications");
    }

    private List<DashboardQuickLinkResponse> ownerQuickLinks() {
        return links("Horses", "Jockeys", "Tournaments", "Registrations", "My Races",
                "Wallet", "Prizes", "Notifications", "Profile");
    }

    private List<DashboardQuickLinkResponse> jockeyQuickLinks() {
        return links("Profile", "Invitations", "My Races", "Performance", "Wallet", "Notifications");
    }

    private List<DashboardQuickLinkResponse> refereeQuickLinks() {
        return links("Assigned Races", "Check-in", "Results", "Reports", "Notifications", "Wallet");
    }

    private List<DashboardQuickLinkResponse> spectatorQuickLinks() {
        return List.of(
                link("Tournaments", true),
                link("Races", true),
                link("Betting", true),
                link("Leaderboard", true),
                link("Wallet", true),
                link("Notifications", true),
                link("Predictions", false),
                link("Shop", false),
                link("Inventory", false)
        );
    }

    private List<DashboardQuickLinkResponse> adminQuickLinks() {
        return links("Users", "Role Applications", "Horse Approval", "Jockey Approval", "Tournaments",
                "Registrations", "Races", "Results", "Finance", "Betting", "Notifications",
                "Audit Logs", "Settings");
    }

    private List<DashboardQuickLinkResponse> links(String... labels) {
        return Arrays.stream(labels).map(label -> link(label, true)).toList();
    }

    private DashboardQuickLinkResponse link(String label, boolean enabled) {
        return DashboardQuickLinkResponse.builder()
                .label(label)
                .route("/" + label.toLowerCase(Locale.ROOT).replace(" ", "-"))
                .enabled(enabled)
                .build();
    }
}
