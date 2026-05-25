package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.WalletResponse;
import com.minhthien.hoser_backend.dto.response.WalletTransactionResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.Wallet;
import com.minhthien.hoser_backend.entity.WalletTransaction;
import com.minhthien.hoser_backend.enums.*;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.repository.WalletRepository;
import com.minhthien.hoser_backend.repository.WalletTransactionRepository;
import com.minhthien.hoser_backend.service.WalletLedgerService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final WalletLedgerService walletLedgerService;

    @Override
    @Transactional
    public Wallet getOrCreateUserWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> createUserWallet(userId));
    }

    @Override
    @Transactional
    public Wallet getOrCreateAdminWallet() {
        return walletRepository.findFirstByOwnerTypeOrderByIdAsc(WalletOwnerType.ADMIN)
                .orElseGet(this::createAdminWallet);
    }

    @Override
    @Transactional
    public WalletResponse getCurrentUserWallet(Long userId) {
        Wallet wallet = getOrCreateUserWallet(userId);
        return mapToWalletResponse(wallet);
    }

    @Override
    @Transactional
    public List<WalletTransactionResponse> getCurrentUserTransactions(Long userId) {
        Wallet wallet = getOrCreateUserWallet(userId);
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    @Override
    @Transactional
    public WalletResponse getAdminWalletResponse() {
        return mapToWalletResponse(getOrCreateAdminWallet());
    }

    @Override
    @Transactional
    public List<WalletTransactionResponse> getAdminWalletTransactions() {
        Wallet adminWallet = getOrCreateAdminWallet();
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(adminWallet.getId()).stream()
                .map(this::mapToTransactionResponse)
                .toList();
    }

    @Override
    @Transactional
    public WalletTransaction credit(Long userId, BigDecimal amount, WalletTransactionType type,
                                    String referenceType, String referenceId, String idempotencyKey,
                                    String metadata, String note) {
        return mutateUserWallet(userId, amount, type, WalletTransactionDirection.CREDIT,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction debit(Long userId, BigDecimal amount, WalletTransactionType type,
                                   String referenceType, String referenceId, String idempotencyKey,
                                   String metadata, String note) {
        return mutateUserWallet(userId, amount, type, WalletTransactionDirection.DEBIT,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction debitAllowNegative(Long userId, BigDecimal amount, WalletTransactionType type,
                                                String referenceType, String referenceId, String idempotencyKey,
                                                String metadata, String note) {
        return mutateUserWalletAllowNegative(userId, amount, type, WalletTransactionDirection.DEBIT,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction hold(Long userId, BigDecimal amount, WalletTransactionType type,
                                  String referenceType, String referenceId, String idempotencyKey,
                                  String metadata, String note) {
        return mutateUserWallet(userId, amount, type, WalletTransactionDirection.HOLD,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction release(Long userId, BigDecimal amount, WalletTransactionType type,
                                     String referenceType, String referenceId, String idempotencyKey,
                                     String metadata, String note) {
        return mutateUserWallet(userId, amount, type, WalletTransactionDirection.RELEASE,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction capture(Long userId, BigDecimal amount, WalletTransactionType type,
                                     String referenceType, String referenceId, String idempotencyKey,
                                     String metadata, String note) {
        return mutateUserWallet(userId, amount, type, WalletTransactionDirection.CAPTURE,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction refund(Long userId, BigDecimal amount, String referenceType,
                                    String referenceId, String idempotencyKey, String metadata, String note) {
        return credit(userId, amount, WalletTransactionType.REFUND,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction creditAdmin(BigDecimal amount, WalletTransactionType type,
                                         String referenceType, String referenceId, String idempotencyKey,
                                         String metadata, String note) {
        return mutateAdminWallet(amount, type, WalletTransactionDirection.CREDIT,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction debitAdmin(BigDecimal amount, WalletTransactionType type,
                                        String referenceType, String referenceId, String idempotencyKey,
                                        String metadata, String note) {
        return mutateAdminWallet(amount, type, WalletTransactionDirection.DEBIT,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction holdAdmin(BigDecimal amount, WalletTransactionType type,
                                       String referenceType, String referenceId, String idempotencyKey,
                                       String metadata, String note) {
        return mutateAdminWallet(amount, type, WalletTransactionDirection.HOLD,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction releaseAdmin(BigDecimal amount, WalletTransactionType type,
                                          String referenceType, String referenceId, String idempotencyKey,
                                          String metadata, String note) {
        return mutateAdminWallet(amount, type, WalletTransactionDirection.RELEASE,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction captureAdmin(BigDecimal amount, WalletTransactionType type,
                                          String referenceType, String referenceId, String idempotencyKey,
                                          String metadata, String note) {
        return mutateAdminWallet(amount, type, WalletTransactionDirection.CAPTURE,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    @Override
    @Transactional
    public WalletTransaction refundAdmin(BigDecimal amount, String referenceType,
                                         String referenceId, String idempotencyKey, String metadata, String note) {
        return creditAdmin(amount, WalletTransactionType.REFUND,
                referenceType, referenceId, idempotencyKey, metadata, note);
    }

    private WalletTransaction mutateUserWallet(Long userId,
                                               BigDecimal amount,
                                               WalletTransactionType type,
                                               WalletTransactionDirection direction,
                                               String referenceType,
                                               String referenceId,
                                               String idempotencyKey,
                                               String metadata,
                                               String note) {
        WalletTransaction existingTransaction = findExistingIdempotentTransaction(idempotencyKey);
        if (existingTransaction != null) {
            return existingTransaction;
        }

        validateAmount(amount);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> createUserWallet(userId));
        return mutateLockedWallet(wallet, amount, type, direction, referenceType, referenceId,
                idempotencyKey, metadata, note);
    }

    private WalletTransaction mutateAdminWallet(BigDecimal amount,
                                                WalletTransactionType type,
                                                WalletTransactionDirection direction,
                                                String referenceType,
                                                String referenceId,
                                                String idempotencyKey,
                                                String metadata,
                                                String note) {
        WalletTransaction existingTransaction = findExistingIdempotentTransaction(idempotencyKey);
        if (existingTransaction != null) {
            return existingTransaction;
        }

        validateAmount(amount);
        Wallet wallet = walletRepository.findByOwnerTypeForUpdate(WalletOwnerType.ADMIN).stream()
                .findFirst()
                .orElseGet(this::createAdminWallet);
        return mutateLockedWallet(wallet, amount, type, direction, referenceType, referenceId,
                idempotencyKey, metadata, note);
    }

    private WalletTransaction mutateUserWalletAllowNegative(Long userId,
                                                            BigDecimal amount,
                                                            WalletTransactionType type,
                                                            WalletTransactionDirection direction,
                                                            String referenceType,
                                                            String referenceId,
                                                            String idempotencyKey,
                                                            String metadata,
                                                            String note) {
        WalletTransaction existingTransaction = findExistingIdempotentTransaction(idempotencyKey);
        if (existingTransaction != null) {
            return existingTransaction;
        }

        validateAmount(amount);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> createUserWallet(userId));
        return mutateLockedWallet(wallet, amount, type, direction, referenceType, referenceId,
                idempotencyKey, metadata, note, true);
    }

    private WalletTransaction mutateLockedWallet(Wallet wallet,
                                                 BigDecimal amount,
                                                 WalletTransactionType type,
                                                 WalletTransactionDirection direction,
                                                 String referenceType,
                                                 String referenceId,
                                                 String idempotencyKey,
                                                 String metadata,
                                                 String note) {
        return mutateLockedWallet(wallet, amount, type, direction, referenceType, referenceId,
                idempotencyKey, metadata, note, false);
    }

    private WalletTransaction mutateLockedWallet(Wallet wallet,
                                                 BigDecimal amount,
                                                 WalletTransactionType type,
                                                 WalletTransactionDirection direction,
                                                 String referenceType,
                                                 String referenceId,
                                                 String idempotencyKey,
                                                 String metadata,
                                                 String note,
                                                 boolean allowNegativeAvailable) {
        validateWalletMutable(wallet);

        BigDecimal availableBefore = wallet.getAvailableBalance();
        BigDecimal holdBefore = wallet.getHoldBalance();
        BigDecimal availableAfter = availableBefore;
        BigDecimal holdAfter = holdBefore;

        switch (direction) {
            case CREDIT -> availableAfter = availableBefore.add(amount);
            case DEBIT -> availableAfter = allowNegativeAvailable
                    ? availableBefore.subtract(amount)
                    : subtractAvailable(availableBefore, amount);
            case HOLD -> {
                availableAfter = subtractAvailable(availableBefore, amount);
                holdAfter = holdBefore.add(amount);
            }
            case RELEASE -> {
                holdAfter = subtractHold(holdBefore, amount);
                availableAfter = availableBefore.add(amount);
            }
            case CAPTURE -> holdAfter = subtractHold(holdBefore, amount);
        }

        wallet.setAvailableBalance(availableAfter);
        wallet.setHoldBalance(holdAfter);
        walletRepository.save(wallet);

        return walletLedgerService.record(wallet, type, direction, amount, availableBefore, availableAfter,
                holdBefore, holdAfter, referenceType, referenceId, idempotencyKey, metadata, note);
    }

    private WalletTransaction findExistingIdempotentTransaction(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return walletTransactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    }

    private Wallet createUserWallet(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Wallet wallet = Wallet.builder()
                .ownerType(WalletOwnerType.USER)
                .user(user)
                .currency(Wallet.DEFAULT_CURRENCY)
                .availableBalance(BigDecimal.ZERO)
                .holdBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
        return walletRepository.save(wallet);
    }

    private Wallet createAdminWallet() {
        Wallet wallet = Wallet.builder()
                .ownerType(WalletOwnerType.ADMIN)
                .user(null)
                .currency(Wallet.DEFAULT_CURRENCY)
                .availableBalance(BigDecimal.ZERO)
                .holdBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
        return walletRepository.save(wallet);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
    }

    private void validateWalletMutable(Wallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BadRequestException("Wallet is not active");
        }
    }

    private BigDecimal subtractAvailable(BigDecimal currentBalance, BigDecimal amount) {
        BigDecimal result = currentBalance.subtract(amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Wallet balance is insufficient");
        }
        return result;
    }

    private BigDecimal subtractHold(BigDecimal currentHold, BigDecimal amount) {
        BigDecimal result = currentHold.subtract(amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Wallet hold balance is insufficient");
        }
        return result;
    }

    private WalletResponse mapToWalletResponse(Wallet wallet) {
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

    private WalletTransactionResponse mapToTransactionResponse(WalletTransaction transaction) {
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
}
