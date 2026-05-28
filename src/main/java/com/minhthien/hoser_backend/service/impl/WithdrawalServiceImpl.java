package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.AdminWalletWithdrawalRequest;
import com.minhthien.hoser_backend.dto.request.CreateWithdrawalRequest;
import com.minhthien.hoser_backend.dto.request.WithdrawalDecisionRequest;
import com.minhthien.hoser_backend.dto.response.AdminWalletWithdrawalResponse;
import com.minhthien.hoser_backend.dto.response.WithdrawalResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.AdminWalletWithdrawal;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.WithdrawalRequest;
import com.minhthien.hoser_backend.enums.AdminWalletWithdrawalStatus;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.enums.WithdrawalStatus;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.AdminWalletWithdrawalRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.repository.WithdrawalRequestRepository;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.WalletService;
import com.minhthien.hoser_backend.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalServiceImpl implements WithdrawalService {

    private static final String USER_WITHDRAWAL_REFERENCE = "USER_WITHDRAWAL";
    private static final String ADMIN_WITHDRAWAL_REFERENCE = "ADMIN_WALLET_WITHDRAWAL";

    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final AdminWalletWithdrawalRepository adminWalletWithdrawalRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private NotificationService notificationService;
    private MailService mailService;

    @Autowired(required = false)
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Autowired(required = false)
    void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    @Transactional
    public WithdrawalResponse createUserWithdrawal(Long userId, CreateWithdrawalRequest request) {
        validateAmount(request.getAmount());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .user(user)
                .amount(request.getAmount())
                .currency(WithdrawalRequest.DEFAULT_CURRENCY)
                .status(WithdrawalStatus.PENDING)
                .bankName(request.getBankName())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankAccountName(request.getBankAccountName())
                .reason(request.getReason())
                .createdBy(user.getUsername())
                .updatedBy(user.getUsername())
                .build();
        withdrawal = withdrawalRequestRepository.save(withdrawal);

        String referenceId = withdrawal.getId().toString();
        walletService.hold(userId, withdrawal.getAmount(), WalletTransactionType.WITHDRAW,
                USER_WITHDRAWAL_REFERENCE, referenceId, "withdraw:user:hold:" + referenceId,
                null, "Withdrawal requested");
        notifyAdmins(NotificationType.WITHDRAWAL_CREATED, "Withdrawal requested",
                user.getUsername() + " requested a withdrawal", withdrawal);
        return mapToResponse(withdrawal);
    }

    @Override
    public List<WithdrawalResponse> getUserWithdrawals(Long userId) {
        return withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public WithdrawalResponse getUserWithdrawal(Long userId, Long withdrawalId) {
        return withdrawalRequestRepository.findByIdAndUserId(withdrawalId, userId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("WithdrawalRequest", "id", withdrawalId));
    }

    @Override
    public List<WithdrawalResponse> getAdminWithdrawals(WithdrawalStatus status) {
        List<WithdrawalRequest> withdrawals = status == null
                ? withdrawalRequestRepository.findAllByOrderByCreatedAtDesc()
                : withdrawalRequestRepository.findByStatusOrderByCreatedAtDesc(status);
        return withdrawals.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public WithdrawalResponse getAdminWithdrawal(Long withdrawalId) {
        return mapToResponse(getWithdrawal(withdrawalId));
    }

    @Override
    @Transactional
    public WithdrawalResponse approveWithdrawal(Long withdrawalId, Long adminId, WithdrawalDecisionRequest request) {
        WithdrawalRequest withdrawal = getWithdrawal(withdrawalId);
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING) {
            throw new BadRequestException("Only pending withdrawals can be approved");
        }
        withdrawal.setStatus(WithdrawalStatus.APPROVED);
        withdrawal.setApprovedBy(adminId);
        withdrawal.setApprovedAt(LocalDateTime.now());
        withdrawal.setAdminNote(note(request));
        audit(adminId, "WITHDRAWAL_APPROVED", USER_WITHDRAWAL_REFERENCE, withdrawalId.toString(),
                withdrawal.getAmount(), note(request));
        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        notifyUser(saved.getUser(), NotificationType.WITHDRAWAL_APPROVED, "Withdrawal approved",
                "Your withdrawal request was approved", saved);
        sendWithdrawalEmail(saved.getUser(), "APPROVED", saved);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WithdrawalResponse rejectWithdrawal(Long withdrawalId, Long adminId, WithdrawalDecisionRequest request) {
        WithdrawalRequest withdrawal = getWithdrawal(withdrawalId);
        if (withdrawal.getStatus() != WithdrawalStatus.PENDING && withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
            throw new BadRequestException("Only pending or approved withdrawals can be rejected");
        }
        String referenceId = withdrawal.getId().toString();
        walletService.release(withdrawal.getUser().getId(), withdrawal.getAmount(), WalletTransactionType.REFUND,
                USER_WITHDRAWAL_REFERENCE, referenceId, "withdraw:user:release:" + referenceId,
                null, "Withdrawal rejected");
        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setRejectedBy(adminId);
        withdrawal.setRejectedAt(LocalDateTime.now());
        withdrawal.setAdminNote(note(request));
        audit(adminId, "WITHDRAWAL_REJECTED", USER_WITHDRAWAL_REFERENCE, referenceId,
                withdrawal.getAmount(), note(request));
        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        notifyUser(saved.getUser(), NotificationType.WITHDRAWAL_REJECTED, "Withdrawal rejected",
                "Your withdrawal request was rejected", saved);
        sendWithdrawalEmail(saved.getUser(), "REJECTED", saved);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WithdrawalResponse markWithdrawalPaid(Long withdrawalId, Long adminId, WithdrawalDecisionRequest request) {
        WithdrawalRequest withdrawal = getWithdrawal(withdrawalId);
        if (withdrawal.getStatus() != WithdrawalStatus.APPROVED) {
            throw new BadRequestException("Only approved withdrawals can be marked paid");
        }
        String referenceId = withdrawal.getId().toString();
        walletService.capture(withdrawal.getUser().getId(), withdrawal.getAmount(), WalletTransactionType.WITHDRAW,
                USER_WITHDRAWAL_REFERENCE, referenceId, "withdraw:user:capture:" + referenceId,
                null, "Withdrawal paid");
        walletService.debitAdmin(withdrawal.getAmount(), WalletTransactionType.WITHDRAW,
                USER_WITHDRAWAL_REFERENCE, referenceId, "withdraw:admin:debit:" + referenceId,
                null, "User withdrawal paid");
        withdrawal.setStatus(WithdrawalStatus.PAID);
        withdrawal.setPaidBy(adminId);
        withdrawal.setPaidAt(LocalDateTime.now());
        withdrawal.setAdminNote(note(request));
        audit(adminId, "WITHDRAWAL_MARKED_PAID", USER_WITHDRAWAL_REFERENCE, referenceId,
                withdrawal.getAmount(), note(request));
        WithdrawalRequest saved = withdrawalRequestRepository.save(withdrawal);
        notifyUser(saved.getUser(), NotificationType.WITHDRAWAL_PAID, "Withdrawal paid",
                "Your withdrawal request was marked paid", saved);
        sendWithdrawalEmail(saved.getUser(), "PAID", saved);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public AdminWalletWithdrawalResponse createAdminWithdrawal(Long adminId, AdminWalletWithdrawalRequest request) {
        validateAmount(request.getAmount());
        AdminWalletWithdrawal withdrawal = AdminWalletWithdrawal.builder()
                .adminId(adminId)
                .amount(request.getAmount())
                .currency(AdminWalletWithdrawal.DEFAULT_CURRENCY)
                .status(AdminWalletWithdrawalStatus.PAID)
                .bankName(request.getBankName())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankAccountName(request.getBankAccountName())
                .reason(request.getReason())
                .paidAt(LocalDateTime.now())
                .createdBy("ADMIN:" + adminId)
                .updatedBy("ADMIN:" + adminId)
                .build();
        withdrawal = adminWalletWithdrawalRepository.save(withdrawal);

        String referenceId = withdrawal.getId().toString();
        walletService.debitAdmin(withdrawal.getAmount(), WalletTransactionType.ADMIN_WITHDRAW,
                ADMIN_WITHDRAWAL_REFERENCE, referenceId, "admin-withdraw:debit:" + referenceId,
                null, "Admin wallet withdrawal");
        audit(adminId, "ADMIN_WALLET_WITHDRAWAL_PAID", ADMIN_WITHDRAWAL_REFERENCE, referenceId,
                withdrawal.getAmount(), request.getReason());
        return mapToResponse(withdrawal);
    }

    @Override
    public List<AdminWalletWithdrawalResponse> getAdminWalletWithdrawals() {
        return adminWalletWithdrawalRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private WithdrawalRequest getWithdrawal(Long withdrawalId) {
        return withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("WithdrawalRequest", "id", withdrawalId));
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
    }

    private void audit(Long adminId, String action, String referenceType, String referenceId,
                       BigDecimal amount, String reason) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .amount(amount)
                .reason(reason)
                .build());
    }

    private String note(WithdrawalDecisionRequest request) {
        return request == null ? null : request.getNote();
    }

    private void notifyAdmins(NotificationType type, String title, String message, WithdrawalRequest withdrawal) {
        userRepository.findByRole(UserRole.ADMIN).forEach(admin ->
                notifyUser(admin, type, title, message, withdrawal));
    }

    private void notifyUser(User recipient, NotificationType type, String title, String message,
                            WithdrawalRequest withdrawal) {
        if (notificationService == null) {
            return;
        }
        try {
            notificationService.notify(recipient, type, title, message, USER_WITHDRAWAL_REFERENCE,
                    String.valueOf(withdrawal.getId()),
                    "{\"amount\":\"%s\",\"status\":\"%s\"}".formatted(
                            withdrawal.getAmount(), withdrawal.getStatus()));
        } catch (RuntimeException ex) {
            log.warn("Could not notify withdrawal event: withdrawalId={}, type={}",
                    withdrawal.getId(), type, ex);
        }
    }

    private void sendWithdrawalEmail(User recipient, String status, WithdrawalRequest withdrawal) {
        if (mailService == null) {
            return;
        }
        try {
            mailService.sendWithdrawalStatus(recipient, status, USER_WITHDRAWAL_REFERENCE,
                    String.valueOf(withdrawal.getId()));
        } catch (RuntimeException ex) {
            log.warn("Could not send withdrawal email: withdrawalId={}, status={}",
                    withdrawal.getId(), status, ex);
        }
    }

    private WithdrawalResponse mapToResponse(WithdrawalRequest withdrawal) {
        return WithdrawalResponse.builder()
                .id(withdrawal.getId())
                .userId(withdrawal.getUser().getId())
                .amount(withdrawal.getAmount())
                .currency(withdrawal.getCurrency())
                .status(withdrawal.getStatus())
                .bankName(withdrawal.getBankName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .bankAccountName(withdrawal.getBankAccountName())
                .reason(withdrawal.getReason())
                .adminNote(withdrawal.getAdminNote())
                .approvedBy(withdrawal.getApprovedBy())
                .rejectedBy(withdrawal.getRejectedBy())
                .paidBy(withdrawal.getPaidBy())
                .approvedAt(withdrawal.getApprovedAt())
                .rejectedAt(withdrawal.getRejectedAt())
                .paidAt(withdrawal.getPaidAt())
                .createdAt(withdrawal.getCreatedAt())
                .updatedAt(withdrawal.getUpdatedAt())
                .build();
    }

    private AdminWalletWithdrawalResponse mapToResponse(AdminWalletWithdrawal withdrawal) {
        return AdminWalletWithdrawalResponse.builder()
                .id(withdrawal.getId())
                .adminId(withdrawal.getAdminId())
                .amount(withdrawal.getAmount())
                .currency(withdrawal.getCurrency())
                .status(withdrawal.getStatus())
                .bankName(withdrawal.getBankName())
                .bankAccountNumber(withdrawal.getBankAccountNumber())
                .bankAccountName(withdrawal.getBankAccountName())
                .reason(withdrawal.getReason())
                .paidAt(withdrawal.getPaidAt())
                .createdAt(withdrawal.getCreatedAt())
                .build();
    }
}
