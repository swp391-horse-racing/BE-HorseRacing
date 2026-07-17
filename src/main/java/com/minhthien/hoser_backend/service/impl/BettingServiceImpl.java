package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.BetMarketRequest;
import com.minhthien.hoser_backend.dto.request.BetRequest;
import com.minhthien.hoser_backend.dto.response.BetMarketResponse;
import com.minhthien.hoser_backend.dto.response.BetOptionResponse;
import com.minhthien.hoser_backend.dto.response.BetResponse;
import com.minhthien.hoser_backend.entity.Bet;
import com.minhthien.hoser_backend.entity.BetMarket;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceParticipant;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.BetMarketStatus;
import com.minhthien.hoser_backend.enums.BetStatus;
import com.minhthien.hoser_backend.enums.RaceParticipantStatus;
import com.minhthien.hoser_backend.enums.RaceStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.FeatureDisabledException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.BetMarketRepository;
import com.minhthien.hoser_backend.repository.BetRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.BettingService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BettingServiceImpl implements BettingService {
    private static final String BET_REF = "BET";
    private static final BigDecimal FIXED_PAYOUT_MULTIPLIER = new BigDecimal("2.00");
    private static final BigDecimal HUNDRED_PERCENT = new BigDecimal("100.00");

    private final BetMarketRepository betMarketRepository;
    private final BetRepository betRepository;
    private final RaceRepository raceRepository;
    private final RaceParticipantRepository raceParticipantRepository;
    private final RaceResultRepository raceResultRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final FinanceSettingsService financeSettingsService;

    @Override
    @Transactional
    public BetMarketResponse createBetMarket(Long adminId, Long raceId, BetMarketRequest request) {
        requireBettingEnabled();
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can create bet markets");
        validateMarketRequest(request);
        Race race = requireRace(raceId);
        validateRaceCanHaveMarket(race);
        if (betMarketRepository.existsByRaceIdAndStatusIn(raceId, activeMarketStatuses())) {
            throw new BadRequestException("Race already has an active bet market");
        }

        BetMarket market = BetMarket.builder()
                .race(race)
                .createdByAdmin(admin)
                .minStake(request.getMinStake())
                .maxStake(request.getMaxStake())
                .note(request.getNote())
                .status(BetMarketStatus.DRAFT)
                .build();
        return mapMarket(betMarketRepository.save(market), true);
    }

    @Override
    @Transactional
    public BetMarketResponse openBetMarket(Long adminId, Long marketId) {
        requireBettingEnabled();
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can open bet markets");
        BetMarket market = requireMarket(marketId);
        if (market.getStatus() != BetMarketStatus.DRAFT && market.getStatus() != BetMarketStatus.CLOSED) {
            throw new BadRequestException("Only draft or closed bet markets can be opened");
        }
        validateRaceOpenForBetting(market.getRace());
        market.setStatus(BetMarketStatus.OPEN);
        market.setOpenedAt(LocalDateTime.now());
        return mapMarket(betMarketRepository.save(market), true);
    }

    @Override
    @Transactional
    public BetMarketResponse closeBetMarket(Long adminId, Long marketId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can close bet markets");
        BetMarket market = requireMarket(marketId);
        if (market.getStatus() != BetMarketStatus.OPEN) {
            throw new BadRequestException("Only open bet markets can be closed");
        }
        market.setStatus(BetMarketStatus.CLOSED);
        market.setClosedAt(LocalDateTime.now());
        return mapMarket(betMarketRepository.save(market), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BetMarketResponse> getAdminBetMarkets(Long adminId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view bet markets");
        return betMarketRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(market -> mapMarket(market, true))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BetResponse> getAdminMarketBets(Long adminId, Long marketId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view market bets");
        if (!betMarketRepository.existsById(marketId)) {
            throw new ResourceNotFoundException("BetMarket", "id", marketId);
        }
        return betRepository.findByMarketIdOrderByPlacedAtDesc(marketId).stream()
                .map(this::mapBet)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BetResponse> getAdminBets(Long adminId, Long raceId) {
        requireRole(requireUser(adminId), UserRole.ADMIN, "Only admins can view bets");
        List<Bet> bets = raceId == null
                ? betRepository.findAllByOrderByPlacedAtDesc()
                : betRepository.findByRaceIdOrderByPlacedAtDesc(raceId);
        return bets.stream()
                .map(this::mapBet)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BetMarketResponse getPublicOpenBetMarket(Long raceId) {
        requireBettingEnabled();
        BetMarket market = betMarketRepository.findByRaceIdAndStatus(raceId, BetMarketStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Open BetMarket", "raceId", raceId));
        validateRaceOpenForBetting(market.getRace());
        return mapMarket(market, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BetMarketResponse> getBettableRaceMarkets(Long userId) {
        requireBettingEnabled();
        User user = requireUser(userId);
        if (user.getRole() != UserRole.SPECTATOR && user.getRole() != UserRole.USER) {
            throw new UnauthorizedException("Only users or spectators can view bettable races");
        }
        LocalDateTime now = LocalDateTime.now();
        return betMarketRepository.findByStatusOrderByRaceScheduledStartAtAsc(BetMarketStatus.OPEN).stream()
                .filter(market -> market.getRace().getStatus() == RaceStatus.SCHEDULED)
                .filter(market -> now.isBefore(market.getRace().getScheduledStartAt()))
                .filter(market -> !raceResultRepository.existsByRaceId(market.getRace().getId()))
                .filter(market -> !raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(
                        market.getRace().getId()).isEmpty())
                .map(market -> mapMarket(market, true))
                .toList();
    }

    @Override
    @Transactional
    public BetResponse placeBet(Long userId, Long raceId, BetRequest request) {
        requireBettingEnabled();
        User user = requireUser(userId);
        requireRole(user, UserRole.SPECTATOR, "Only spectators can place bets");
        if (request == null || request.getParticipantId() == null || request.getStakeAmount() == null) {
            throw new BadRequestException("Bet request is required");
        }
        BetMarket market = betMarketRepository.findByRaceIdAndStatus(raceId, BetMarketStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Open BetMarket", "raceId", raceId));
        validateRaceOpenForBetting(market.getRace());
        validateStake(market, request.getStakeAmount());

        RaceParticipant participant = raceParticipantRepository.findById(request.getParticipantId())
                .orElseThrow(() -> new ResourceNotFoundException("RaceParticipant", "id",
                        request.getParticipantId()));
        if (!participant.getRace().getId().equals(raceId)) {
            throw new BadRequestException("Participant does not belong to this race");
        }

        Bet bet = Bet.builder()
                .market(market)
                .race(market.getRace())
                .participant(participant)
                .user(user)
                .stakeAmount(request.getStakeAmount())
                .potentialPayoutAmount(request.getStakeAmount().multiply(FIXED_PAYOUT_MULTIPLIER))
                .status(BetStatus.PLACED)
                .placedAt(LocalDateTime.now())
                .build();
        Bet saved = betRepository.save(bet);
        String holdKey = "bet:%d:stake-hold".formatted(saved.getId());
        walletService.hold(userId, saved.getStakeAmount(), WalletTransactionType.BET_STAKE,
                BET_REF, String.valueOf(saved.getId()), holdKey, null, "Bet stake held");
        saved.setStakeHoldKey(holdKey);
        return mapBet(betRepository.save(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BetResponse> getUserBets(Long userId) {
        requireUser(userId);
        return betRepository.findByUserIdOrderByPlacedAtDesc(userId).stream()
                .map(this::mapBet)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BetResponse getUserBet(Long userId, Long betId) {
        requireUser(userId);
        Bet bet = requireBet(betId);
        if (!bet.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Cannot view another user's bet");
        }
        return mapBet(bet);
    }

    @Override
    @Transactional
    public void lockRaceBets(Long raceId) {
        Optional<BetMarket> marketOptional = betMarketRepository
                .findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(raceId,
                        List.of(BetMarketStatus.OPEN, BetMarketStatus.CLOSED));
        if (marketOptional.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        BetMarket market = marketOptional.get();
        if (market.getStatus() == BetMarketStatus.OPEN) {
            market.setStatus(BetMarketStatus.CLOSED);
            market.setClosedAt(now);
            betMarketRepository.save(market);
        }
        betRepository.findByRaceIdAndStatusIn(raceId, List.of(BetStatus.PLACED)).forEach(bet -> {
            bet.setStatus(BetStatus.LOCKED);
            bet.setLockedAt(now);
            betRepository.save(bet);
        });
    }

    @Override
    @Transactional
    public void settleRaceBets(Long raceId) {
        Optional<BetMarket> marketOptional = betMarketRepository
                .findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(raceId,
                        List.of(BetMarketStatus.OPEN, BetMarketStatus.CLOSED, BetMarketStatus.SETTLED));
        if (marketOptional.isEmpty()) {
            return;
        }
        Race race = requireRace(raceId);
        if (race.getStatus() != RaceStatus.RESULT_CONFIRMED) {
            throw new BadRequestException("Race result must be confirmed before settling bets");
        }
        List<RaceResult> results = raceResultRepository.findByRaceIdOrderByRankAsc(raceId);
        Optional<Long> winningParticipantId = results.stream()
                .filter(result -> result.getStatus() == RaceParticipantStatus.FINISHED)
                .filter(result -> Integer.valueOf(1).equals(result.getRank()))
                .map(result -> result.getParticipant().getId())
                .findFirst();

        List<Bet> bets = betRepository.findByRaceIdAndStatusIn(raceId,
                List.of(BetStatus.PLACED, BetStatus.LOCKED, BetStatus.UNPAID));
        LocalDateTime now = LocalDateTime.now();
        for (Bet bet : bets) {
            if (bet.getStatus() == BetStatus.UNPAID) {
                retryUnpaidProfit(bet, now);
            } else if (winningParticipantId.isPresent()
                    && winningParticipantId.get().equals(bet.getParticipant().getId())) {
                settleWinningBet(bet, now);
            } else {
                settleLosingBet(bet, now);
            }
        }

        BetMarket market = marketOptional.get();
        if (market.getStatus() != BetMarketStatus.SETTLED) {
            market.setStatus(BetMarketStatus.SETTLED);
            market.setSettledAt(now);
            betMarketRepository.save(market);
        }
    }

    @Override
    @Transactional
    public void cancelRaceBets(Long raceId) {
        Optional<BetMarket> marketOptional = betMarketRepository
                .findFirstByRaceIdAndStatusInOrderByCreatedAtDesc(raceId,
                        List.of(BetMarketStatus.DRAFT, BetMarketStatus.OPEN, BetMarketStatus.CLOSED));
        LocalDateTime now = LocalDateTime.now();
        marketOptional.ifPresent(market -> {
            market.setStatus(BetMarketStatus.CANCELLED);
            market.setCancelledAt(now);
            betMarketRepository.save(market);
        });
        betRepository.findByRaceIdAndStatusIn(raceId, List.of(BetStatus.PLACED, BetStatus.LOCKED))
                .forEach(bet -> {
                    releaseCancelledStakeIfNeeded(bet);
                    bet.setStatus(BetStatus.CANCELLED);
                    bet.setSettledAt(now);
                    betRepository.save(bet);
                });
    }

    private void settleLosingBet(Bet bet, LocalDateTime now) {
        String captureKey = "bet:%d:stake-capture".formatted(bet.getId());
        String adminCreditKey = "bet:%d:stake-admin-credit".formatted(bet.getId());
        walletService.capture(bet.getUser().getId(), bet.getStakeAmount(), WalletTransactionType.BET_STAKE,
                BET_REF, String.valueOf(bet.getId()), captureKey, null, "Losing bet stake captured");
        walletService.creditAdmin(bet.getStakeAmount(), WalletTransactionType.BET_STAKE,
                BET_REF, String.valueOf(bet.getId()), adminCreditKey, null, "Losing bet stake received");
        bet.setStakeCaptureKey(captureKey);
        bet.setAdminStakeCreditKey(adminCreditKey);
        bet.setStatus(BetStatus.LOST);
        bet.setSettledAt(now);
        betRepository.save(bet);
    }

    private void settleWinningBet(Bet bet, LocalDateTime now) {
        releaseWinningStakeIfNeeded(bet);
        prepareWinningProfitSnapshotIfNeeded(bet);
        if (!adminWalletCanPay(bet.getNetProfitAmount())) {
            bet.setStatus(BetStatus.UNPAID);
            bet.setSettledAt(now);
            betRepository.save(bet);
            return;
        }
        payWinningProfit(bet, now);
    }

    private void retryUnpaidProfit(Bet bet, LocalDateTime now) {
        prepareWinningProfitSnapshotIfNeeded(bet);
        if (!adminWalletCanPay(bet.getNetProfitAmount())) {
            return;
        }
        payWinningProfit(bet, now);
    }

    private void releaseWinningStakeIfNeeded(Bet bet) {
        if (bet.getStakeReleaseKey() != null && !bet.getStakeReleaseKey().isBlank()) {
            return;
        }
        String releaseKey = "bet:%d:stake-release".formatted(bet.getId());
        walletService.release(bet.getUser().getId(), bet.getStakeAmount(), WalletTransactionType.BET_STAKE,
                BET_REF, String.valueOf(bet.getId()), releaseKey, null, "Winning bet stake released");
        bet.setStakeReleaseKey(releaseKey);
    }

    private void releaseCancelledStakeIfNeeded(Bet bet) {
        if (bet.getStakeReleaseKey() != null && !bet.getStakeReleaseKey().isBlank()) {
            return;
        }
        String releaseKey = "bet:%d:stake-cancel-release".formatted(bet.getId());
        walletService.release(bet.getUser().getId(), bet.getStakeAmount(), WalletTransactionType.BET_STAKE,
                BET_REF, String.valueOf(bet.getId()), releaseKey, null, "Cancelled race bet stake released");
        bet.setStakeReleaseKey(releaseKey);
    }

    private void payWinningProfit(Bet bet, LocalDateTime now) {
        BigDecimal netProfitAmount = defaultZero(bet.getNetProfitAmount());
        if (netProfitAmount.compareTo(BigDecimal.ZERO) > 0) {
            String adminDebitKey = "bet:%d:profit-admin-debit".formatted(bet.getId());
            String profitCreditKey = "bet:%d:profit-credit".formatted(bet.getId());
            walletService.debitAdmin(netProfitAmount, WalletTransactionType.BET_PAYOUT,
                    BET_REF, String.valueOf(bet.getId()), adminDebitKey, null, "Winning bet profit paid");
            walletService.credit(bet.getUser().getId(), netProfitAmount, WalletTransactionType.BET_PAYOUT,
                    BET_REF, String.valueOf(bet.getId()), profitCreditKey, null, "Winning bet profit received");
            bet.setProfitAdminDebitKey(adminDebitKey);
            bet.setProfitCreditKey(profitCreditKey);
        }
        bet.setStatus(BetStatus.WON);
        bet.setSettledAt(now);
        betRepository.save(bet);
    }

    private void prepareWinningProfitSnapshotIfNeeded(Bet bet) {
        if (bet.getGrossProfitAmount() != null
                && bet.getWinningTaxPercent() != null
                && bet.getWinningTaxAmount() != null
                && bet.getNetProfitAmount() != null) {
            return;
        }
        BigDecimal grossProfitAmount = bet.getStakeAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxPercent = financeSettingsService.getBetWinningTaxPercent()
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = calculatePercentAmount(grossProfitAmount, taxPercent);
        BigDecimal netProfitAmount = grossProfitAmount.subtract(taxAmount).setScale(2, RoundingMode.HALF_UP);
        bet.setGrossProfitAmount(grossProfitAmount);
        bet.setWinningTaxPercent(taxPercent);
        bet.setWinningTaxAmount(taxAmount);
        bet.setNetProfitAmount(netProfitAmount);
    }

    private BigDecimal calculatePercentAmount(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent)
                .divide(HUNDRED_PERCENT, 2, RoundingMode.HALF_UP);
    }

    private void validateMarketRequest(BetMarketRequest request) {
        if (request == null) {
            throw new BadRequestException("Bet market request is required");
        }
        if (request.getMinStake() == null || request.getMaxStake() == null) {
            throw new BadRequestException("Bet market stake limits are required");
        }
        if (request.getMinStake().compareTo(BigDecimal.ZERO) <= 0
                || request.getMaxStake().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Bet market stake limits must be greater than zero");
        }
        if (request.getMinStake().compareTo(request.getMaxStake()) > 0) {
            throw new BadRequestException("Minimum stake must not exceed maximum stake");
        }
    }

    private void validateRaceCanHaveMarket(Race race) {
        if (race.getStatus() == RaceStatus.RESULT_CONFIRMED || race.getStatus() == RaceStatus.CANCELLED
                || raceResultRepository.existsByRaceId(race.getId())) {
            throw new BadRequestException("Race cannot create a bet market after result or cancellation");
        }
        if (raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId()).isEmpty()) {
            throw new BadRequestException("Race must have participants before creating a bet market");
        }
    }

    private void validateRaceOpenForBetting(Race race) {
        if (race.getStatus() != RaceStatus.SCHEDULED) {
            throw new BadRequestException("Bets can only be placed on scheduled races");
        }
        if (!LocalDateTime.now().isBefore(race.getScheduledStartAt())) {
            throw new BadRequestException("Betting is closed for this race");
        }
        validateRaceCanHaveMarket(race);
    }

    private void validateStake(BetMarket market, BigDecimal stakeAmount) {
        if (stakeAmount.compareTo(market.getMinStake()) < 0) {
            throw new BadRequestException("Stake amount is below market minimum");
        }
        if (stakeAmount.compareTo(market.getMaxStake()) > 0) {
            throw new BadRequestException("Stake amount exceeds market maximum");
        }
    }

    private boolean adminWalletCanPay(BigDecimal amount) {
        if (defaultZero(amount).compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return walletService.getOrCreateAdminWallet().getAvailableBalance().compareTo(amount) >= 0;
    }

    private BigDecimal defaultZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BetMarketResponse mapMarket(BetMarket market, boolean includeOptions) {
        Race race = market.getRace();
        return BetMarketResponse.builder()
                .id(market.getId())
                .raceId(race.getId())
                .raceName(race.getName())
                .tournamentId(race.getTournament().getId())
                .tournamentName(race.getTournament().getName())
                .status(market.getStatus())
                .minStake(market.getMinStake())
                .maxStake(market.getMaxStake())
                .note(market.getNote())
                .createdByAdminId(market.getCreatedByAdmin().getId())
                .createdByAdminUsername(market.getCreatedByAdmin().getUsername())
                .openedAt(market.getOpenedAt())
                .closedAt(market.getClosedAt())
                .settledAt(market.getSettledAt())
                .cancelledAt(market.getCancelledAt())
                .createdAt(market.getCreatedAt())
                .updatedAt(market.getUpdatedAt())
                .options(includeOptions ? raceParticipantRepository.findByRaceIdOrderByGateNumberAsc(race.getId())
                        .stream()
                        .map(this::mapOption)
                        .toList() : null)
                .build();
    }

    private BetOptionResponse mapOption(RaceParticipant participant) {
        return BetOptionResponse.builder()
                .participantId(participant.getId())
                .horseId(participant.getHorse().getId())
                .horseName(participant.getHorse().getName())
                .jockeyId(participant.getJockey().getId())
                .jockeyUsername(participant.getJockey().getUsername())
                .gateNumber(participant.getGateNumber())
                .status(participant.getStatus())
                .build();
    }

    private BetResponse mapBet(Bet bet) {
        return BetResponse.builder()
                .id(bet.getId())
                .marketId(bet.getMarket().getId())
                .raceId(bet.getRace().getId())
                .raceName(bet.getRace().getName())
                .participantId(bet.getParticipant().getId())
                .horseId(bet.getParticipant().getHorse().getId())
                .horseName(bet.getParticipant().getHorse().getName())
                .userId(bet.getUser().getId())
                .username(bet.getUser().getUsername())
                .stakeAmount(bet.getStakeAmount())
                .potentialPayoutAmount(bet.getPotentialPayoutAmount())
                .winningTaxPercent(bet.getWinningTaxPercent())
                .winningTaxAmount(bet.getWinningTaxAmount())
                .grossProfitAmount(bet.getGrossProfitAmount())
                .netProfitAmount(bet.getNetProfitAmount())
                .status(bet.getStatus())
                .placedAt(bet.getPlacedAt())
                .lockedAt(bet.getLockedAt())
                .settledAt(bet.getSettledAt())
                .build();
    }

    private BetMarket requireMarket(Long marketId) {
        return betMarketRepository.findById(marketId)
                .orElseThrow(() -> new ResourceNotFoundException("BetMarket", "id", marketId));
    }

    private Bet requireBet(Long betId) {
        return betRepository.findById(betId)
                .orElseThrow(() -> new ResourceNotFoundException("Bet", "id", betId));
    }

    private Race requireRace(Long raceId) {
        return raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("Race", "id", raceId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) {
            throw new UnauthorizedException(message);
        }
    }

    private void requireBettingEnabled() {
        if (!financeSettingsService.isBettingEnabled()) {
            throw new FeatureDisabledException("Betting feature is disabled");
        }
    }

    private List<BetMarketStatus> activeMarketStatuses() {
        return List.of(BetMarketStatus.DRAFT, BetMarketStatus.OPEN, BetMarketStatus.CLOSED);
    }
}
