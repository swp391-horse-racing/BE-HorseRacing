package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.*;
import com.minhthien.hoser_backend.entity.*;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.DashboardService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private static final int RECENT_LIMIT = 10;

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
    private final RaceResultRepository raceResultRepository;
    private final BetRepository betRepository;
    private final BetMarketRepository betMarketRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentCallbackLogRepository paymentCallbackLogRepository;
    private final AdminWalletWithdrawalRepository adminWalletWithdrawalRepository;
    private final RaceComplaintRepository raceComplaintRepository;
    private final WalletService walletService;

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
        summary.put("openTournamentCount", tournamentRepository
                .findByStatusOrderByCreatedAtDesc(TournamentStatus.OPEN_REGISTRATION).size());

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
    @Transactional
    public DashboardResponse getSpectatorDashboard(Long userId) {
        User user = requireRole(userId, UserRole.SPECTATOR);
        List<Bet> bets = betRepository.findByUserIdOrderByPlacedAtDesc(userId);
        List<BetMarket> openMarkets = betMarketRepository.findByStatusOrderByRaceScheduledStartAtAsc(BetMarketStatus.OPEN);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openTournamentCount", tournamentRepository
                .findByStatusOrderByCreatedAtDesc(TournamentStatus.OPEN_REGISTRATION).size());
        summary.put("openBetMarketCount", openMarkets.size());
        summary.put("betsByStatus", countBy(bets, bet -> bet.getStatus().name()));
        summary.put("totalBetStake", sum(bets, Bet::getStakeAmount));
        summary.put("totalBetPayout", sum(bets, Bet::getNetProfitAmount));
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
        List<User> users = userRepository.findAll();
        List<WithdrawalRequest> withdrawals = withdrawalRequestRepository.findAllByOrderByCreatedAtDesc();
        List<Race> races = raceRepository.findAll();
        LocalDate today = LocalDate.now();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("usersByRole", countBy(users, u -> u.getRole().name()));
        summary.put("activeUserCount", users.stream().filter(User::getActive).count());
        summary.put("deactivatedUserCount", users.stream().filter(u -> !Boolean.TRUE.equals(u.getActive())).count());
        summary.put("pendingRoleApplicationCount", users.stream()
                .filter(u -> u.getRoleApprovalStatus() == RoleApprovalStatus.PENDING).count());
        summary.put("pendingHorseCount", horseRepository.findByStatusOrderByCreatedAtDesc(HorseStatus.PENDING).size());
        summary.put("pendingJockeyProfileCount", jockeyProfileRepository.findByStatusOrderByCreatedAtDesc(JockeyStatus.PENDING).size());
        summary.put("openTournamentCount", tournamentRepository.findByStatusOrderByCreatedAtDesc(TournamentStatus.OPEN_REGISTRATION).size());
        summary.put("ongoingTournamentCount", tournamentRepository.findByStatusOrderByCreatedAtDesc(TournamentStatus.ONGOING).size());
        summary.put("pendingWithdrawalCount", withdrawals.stream().filter(w -> w.getStatus() == WithdrawalStatus.PENDING).count());
        summary.put("todayRaceCount", races.stream().filter(race -> isSameDay(race.getScheduledStartAt(), today)).count());
        summary.put("pendingComplaintCount", raceComplaintRepository.findAll().stream()
                .filter(complaint -> complaint.getStatus() == RaceComplaintStatus.PENDING).count());
        summary.put("paymentOrdersByStatus", countBy(paymentOrderRepository.findAll(), order -> order.getStatus().name()));
        summary.put("paymentCallbackLogCount", paymentCallbackLogRepository.count());
        summary.put("adminWalletWithdrawalCount", adminWalletWithdrawalRepository.count());

        List<DashboardItemResponse> alerts = new ArrayList<>();
        addAlert(alerts, "ROLE_APPLICATION_PENDING", "Role applications pending review",
                (Long) summary.get("pendingRoleApplicationCount"));
        addAlert(alerts, "HORSE_PENDING", "Horses pending review", (Integer) summary.get("pendingHorseCount"));
        addAlert(alerts, "JOCKEY_PROFILE_PENDING", "Jockey profiles pending review", (Integer) summary.get("pendingJockeyProfileCount"));
        addAlert(alerts, "WITHDRAWAL_PENDING", "Withdrawals pending review", (Long) summary.get("pendingWithdrawalCount"));

        return buildAdminDashboard(user, summary, alerts, adminQuickLinks(), adminUpcomingRaces(races));
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
        return raceRepository.findAll().stream()
                .filter(race -> status == null || race.getStatus() == status)
                .filter(race -> from == null || !race.getScheduledStartAt().isBefore(from))
                .filter(race -> to == null || !race.getScheduledStartAt().isAfter(to))
                .sorted(Comparator.comparing(Race::getScheduledStartAt))
                .map(this::mapRace)
                .toList();
    }

    private DashboardResponse buildDashboard(User user,
                                             Map<String, Object> businessSummary,
                                             List<DashboardItemResponse> alerts,
                                             List<DashboardQuickLinkResponse> quickLinks,
                                             List<DashboardItemResponse> upcoming) {
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseGet(() -> walletService.getOrCreateUserWallet(user.getId()));
        List<WalletTransaction> transactions = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
        List<WithdrawalRequest> withdrawals = withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return DashboardResponse.builder()
                .role(user.getRole())
                .account(account(user))
                .wallet(mapWallet(wallet))
                .moneyIn(sumTransactions(transactions, Set.of(WalletTransactionDirection.CREDIT)))
                .moneyOut(sumTransactions(transactions, Set.of(WalletTransactionDirection.DEBIT, WalletTransactionDirection.CAPTURE)))
                .hold(sumTransactions(transactions, Set.of(WalletTransactionDirection.HOLD)))
                .withdrawals(withdrawalSummary(withdrawals))
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
        List<WalletTransaction> transactions = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
        List<WithdrawalRequest> withdrawals = withdrawalRequestRepository.findAllByOrderByCreatedAtDesc();
        return DashboardResponse.builder()
                .role(user.getRole())
                .account(account(user))
                .wallet(mapWallet(wallet))
                .moneyIn(sumTransactions(transactions, Set.of(WalletTransactionDirection.CREDIT)))
                .moneyOut(sumTransactions(transactions, Set.of(WalletTransactionDirection.DEBIT, WalletTransactionDirection.CAPTURE)))
                .hold(sumTransactions(transactions, Set.of(WalletTransactionDirection.HOLD)))
                .withdrawals(withdrawalSummary(withdrawals))
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
        return raceRegistrationRepository.findAll().stream()
                .filter(registration -> registration.getJockey().getId().equals(jockeyId))
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
        List<RaceResult> results = raceResultRepository.findAll().stream()
                .filter(result -> result.getJockey().getId().equals(jockeyId))
                .toList();
        List<WalletTransaction> transactions = userTransactions(jockeyId);
        return JockeyPerformanceResponse.builder()
                .jockeyId(jockeyId)
                .raceCount((long) races.size())
                .completedRaceCount(results.stream().filter(result -> result.getRace().getResultFinalizedAt() != null).count())
                .firstPlaces(results.stream().filter(result -> Objects.equals(result.getRank(), 1)).count())
                .secondPlaces(results.stream().filter(result -> Objects.equals(result.getRank(), 2)).count())
                .thirdPlaces(results.stream().filter(result -> Objects.equals(result.getRank(), 3)).count())
                .totalJockeyPayout(sum(transactions.stream()
                        .filter(t -> t.getType() == WalletTransactionType.JOCKEY_PAYOUT).toList(), WalletTransaction::getAmount))
                .totalPrizePayout(sum(transactions.stream()
                        .filter(t -> t.getType() == WalletTransactionType.PRIZE_PAYOUT).toList(), WalletTransaction::getAmount))
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

    private Map<String, BigDecimal> sumTransactions(List<WalletTransaction> transactions,
                                                    Set<WalletTransactionDirection> directions) {
        return sumBy(transactions.stream()
                        .filter(transaction -> directions.contains(transaction.getDirection()))
                        .toList(),
                transaction -> transaction.getType().name(),
                WalletTransaction::getAmount);
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
        RaceTrack track = race.getRaceTrack();
        return RaceResponse.builder()
                .id(race.getId())
                .tournamentId(race.getTournament().getId())
                .name(race.getName())
                .distance(race.getDistance())
                .raceTrackId(track == null ? null : track.getId())
                .raceTrackName(track == null ? null : track.getName())
                .raceTrackLocationKey(track == null ? null : track.getLocationKey())
                .raceTrackAddress(track == null ? null : track.getAddress())
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
        flags.put("betting", true);
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
