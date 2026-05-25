package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.WalletResponse;
import com.minhthien.hoser_backend.dto.response.WalletTransactionResponse;
import com.minhthien.hoser_backend.entity.Wallet;
import com.minhthien.hoser_backend.entity.WalletTransaction;
import com.minhthien.hoser_backend.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    Wallet getOrCreateUserWallet(Long userId);

    Wallet getOrCreateAdminWallet();

    WalletResponse getCurrentUserWallet(Long userId);

    List<WalletTransactionResponse> getCurrentUserTransactions(Long userId);

    WalletResponse getAdminWalletResponse();

    List<WalletTransactionResponse> getAdminWalletTransactions();

    WalletTransaction credit(Long userId, BigDecimal amount, WalletTransactionType type,
                             String referenceType, String referenceId, String idempotencyKey,
                             String metadata, String note);

    WalletTransaction debit(Long userId, BigDecimal amount, WalletTransactionType type,
                            String referenceType, String referenceId, String idempotencyKey,
                            String metadata, String note);

    WalletTransaction debitAllowNegative(Long userId, BigDecimal amount, WalletTransactionType type,
                                          String referenceType, String referenceId, String idempotencyKey,
                                          String metadata, String note);

    WalletTransaction hold(Long userId, BigDecimal amount, WalletTransactionType type,
                           String referenceType, String referenceId, String idempotencyKey,
                           String metadata, String note);

    WalletTransaction release(Long userId, BigDecimal amount, WalletTransactionType type,
                              String referenceType, String referenceId, String idempotencyKey,
                              String metadata, String note);

    WalletTransaction capture(Long userId, BigDecimal amount, WalletTransactionType type,
                              String referenceType, String referenceId, String idempotencyKey,
                              String metadata, String note);

    WalletTransaction refund(Long userId, BigDecimal amount, String referenceType,
                             String referenceId, String idempotencyKey, String metadata, String note);

    WalletTransaction creditAdmin(BigDecimal amount, WalletTransactionType type,
                                  String referenceType, String referenceId, String idempotencyKey,
                                  String metadata, String note);

    WalletTransaction debitAdmin(BigDecimal amount, WalletTransactionType type,
                                 String referenceType, String referenceId, String idempotencyKey,
                                 String metadata, String note);

    WalletTransaction holdAdmin(BigDecimal amount, WalletTransactionType type,
                                String referenceType, String referenceId, String idempotencyKey,
                                String metadata, String note);

    WalletTransaction releaseAdmin(BigDecimal amount, WalletTransactionType type,
                                   String referenceType, String referenceId, String idempotencyKey,
                                   String metadata, String note);

    WalletTransaction captureAdmin(BigDecimal amount, WalletTransactionType type,
                                   String referenceType, String referenceId, String idempotencyKey,
                                   String metadata, String note);

    WalletTransaction refundAdmin(BigDecimal amount, String referenceType,
                                  String referenceId, String idempotencyKey, String metadata, String note);
}
