package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.*;
import com.minhthien.hoser_backend.dto.response.AdminAuditLogResponse;
import com.minhthien.hoser_backend.dto.response.WithdrawalResponse;
import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.Wallet;
import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import com.minhthien.hoser_backend.enums.PaymentProvider;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletOwnerType;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.enums.WithdrawalStatus;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.AdminAuditLogService;
import com.minhthien.hoser_backend.service.PaymentService;
import com.minhthien.hoser_backend.service.WalletService;
import com.minhthien.hoser_backend.service.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:phase23-money-flow-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.payment.callback-token=test-callback-token"
})
class Phase2Phase3MoneyFlowIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private PaymentCallbackLogRepository paymentCallbackLogRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private AdminWalletWithdrawalRepository adminWalletWithdrawalRepository;

    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    @Autowired
    private AdminAuditLogService adminAuditLogService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        adminAuditLogRepository.deleteAll();
        adminWalletWithdrawalRepository.deleteAll();
        withdrawalRequestRepository.deleteAll();
        paymentCallbackLogRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
        walletService.getOrCreateAdminWallet();
    }

    @Test
    void depositPaidCreditsUserAndAdminWalletAndDuplicateCallbackDoesNotDoubleCredit() {
        User user = createUser("deposit-user", "deposit-user@example.com", UserRole.USER);
        PaymentOrder order = createPendingDepositOrder(user, "100");

        var paid = paymentService.handleDepositCallback(depositCallback(order.getReferenceCode()));
        paymentService.handleDepositCallback(depositCallback(order.getReferenceCode()));

        assertThat(paid.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(userWallet(user).getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(adminWallet().getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(walletTransactionRepository.findAll()).hasSize(2);
        assertThat(paymentCallbackLogRepository.findByReferenceCodeOrderByProcessedAtDesc(order.getReferenceCode()))
                .hasSize(2)
                .allSatisfy(log -> {
                    assertThat(log.isTokenValid()).isTrue();
                    assertThat(log.isProcessed()).isTrue();
                });
    }

    @Test
    void depositCallbackRejectsInvalidTokenAndCancelledOrderCannotBePaid() {
        User user = createUser("bad-callback-user", "bad-callback-user@example.com", UserRole.USER);
        PaymentOrder order = createPendingDepositOrder(user, "100");
        DepositCallbackRequest badToken = depositCallback(order.getReferenceCode());
        badToken.setCallbackToken("wrong-token");

        assertThatThrownBy(() -> paymentService.handleDepositCallback(badToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid payment callback token");
        assertThat(paymentCallbackLogRepository.findByReferenceCodeOrderByProcessedAtDesc(order.getReferenceCode()))
                .hasSize(1)
                .first()
                .satisfies(log -> {
                    assertThat(log.isTokenValid()).isFalse();
                    assertThat(log.isProcessed()).isFalse();
                    assertThat(log.getErrorMessage()).isEqualTo("Invalid payment callback token");
                });

        DepositCallbackRequest cancel = depositCallback(order.getReferenceCode());
        cancel.setStatus(PaymentOrderStatus.CANCELLED);
        paymentService.handleDepositCallback(cancel);

        assertThatThrownBy(() -> paymentService.handleDepositCallback(depositCallback(order.getReferenceCode())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Payment order cannot be paid from status CANCELLED");
        assertThat(paymentCallbackLogRepository.findByReferenceCodeOrderByProcessedAtDesc(order.getReferenceCode()))
                .hasSize(3);
    }

    @Test
    void depositOrderListsMapLazyUserInsideReadOnlyTransaction() {
        User user = createUser("list-deposit-user", "list-deposit-user@example.com", UserRole.USER);
        createPendingDepositOrder(user, "100");

        assertThat(paymentService.getUserDepositOrders(user.getId()))
                .hasSize(1)
                .first()
                .satisfies(order -> assertThat(order.getUserId()).isEqualTo(user.getId()));
        assertThat(paymentService.getAdminPaymentOrders())
                .extracting(order -> order.getUserId())
                .contains(user.getId());
    }

    @Test
    void userWithdrawalHoldRejectAndMarkPaidFlowsUpdateWalletsAndAudit() {
        User user = createUser("withdraw-user", "withdraw-user@example.com", UserRole.USER);
        User admin = createUser("withdraw-admin", "withdraw-admin@example.com", UserRole.ADMIN);
        walletService.credit(user.getId(), new BigDecimal("100.00"), WalletTransactionType.DEPOSIT,
                "TEST", "user-fund", "user-fund", null, null);

        WithdrawalResponse pending = withdrawalService.createUserWithdrawal(user.getId(), withdrawalRequest("40.00"));
        assertThat(pending.getStatus()).isEqualTo(WithdrawalStatus.PENDING);
        assertThat(userWallet(user).getAvailableBalance()).isEqualByComparingTo("60.00");
        assertThat(userWallet(user).getHoldBalance()).isEqualByComparingTo("40.00");

        withdrawalService.rejectWithdrawal(pending.getId(), admin.getId(), decision("invalid bank"));
        assertThat(userWallet(user).getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(userWallet(user).getHoldBalance()).isEqualByComparingTo("0.00");

        walletService.creditAdmin(new BigDecimal("200.00"), WalletTransactionType.DEPOSIT,
                "TEST", "admin-fund", "admin-fund", null, null);
        WithdrawalResponse second = withdrawalService.createUserWithdrawal(user.getId(), withdrawalRequest("50.00"));
        withdrawalService.approveWithdrawal(second.getId(), admin.getId(), decision("ok"));
        WithdrawalResponse paid = withdrawalService.markWithdrawalPaid(second.getId(), admin.getId(), decision("bank paid"));

        assertThat(paid.getStatus()).isEqualTo(WithdrawalStatus.PAID);
        assertThat(userWallet(user).getAvailableBalance()).isEqualByComparingTo("50.00");
        assertThat(userWallet(user).getHoldBalance()).isEqualByComparingTo("0.00");
        assertThat(adminWallet().getAvailableBalance()).isEqualByComparingTo("150.00");
        assertThat(adminAuditLogRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                "USER_WITHDRAWAL", second.getId().toString())).hasSize(2);
        List<AdminAuditLogResponse> filteredAuditLogs =
                adminAuditLogService.getAdminAuditLogs("USER_WITHDRAWAL", second.getId().toString());
        assertThat(filteredAuditLogs)
                .extracting(AdminAuditLogResponse::getAction)
                .containsExactly("WITHDRAWAL_MARKED_PAID", "WITHDRAWAL_APPROVED");
    }

    @Test
    void adminWalletWithdrawalDebitsAdminWalletAndWritesAudit() {
        User admin = createUser("admin-withdraw", "admin-withdraw@example.com", UserRole.ADMIN);
        walletService.creditAdmin(new BigDecimal("100.00"), WalletTransactionType.DEPOSIT,
                "TEST", "admin-direct-fund", "admin-direct-fund", null, null);

        withdrawalService.createAdminWithdrawal(admin.getId(), adminWithdrawalRequest("30.00"));

        assertThat(adminWallet().getAvailableBalance()).isEqualByComparingTo("70.00");
        assertThat(adminWalletWithdrawalRepository.findAll()).hasSize(1);
        assertThat(adminAuditLogRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                "ADMIN_WALLET_WITHDRAWAL", adminWalletWithdrawalRepository.findAll().get(0).getId().toString()))
                .hasSize(1);
        assertThat(adminAuditLogService.getAdminAuditLogs(null, null))
                .extracting(AdminAuditLogResponse::getAction)
                .contains("ADMIN_WALLET_WITHDRAWAL_PAID");
    }

    private User createUser(String username, String email, UserRole role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(role)
                .active(true)
                .build());
    }

    private PaymentOrder createPendingDepositOrder(User user, String amount) {
        return paymentOrderRepository.save(PaymentOrder.builder()
                .user(user)
                .amount(new BigDecimal(amount))
                .currency(PaymentOrder.DEFAULT_CURRENCY)
                .provider(PaymentProvider.ZALOPAY)
                .status(PaymentOrderStatus.PENDING)
                .referenceCode("DEP-" + user.getUsername())
                .transferContent("HORSE DEP-" + user.getUsername())
                .createdBy(user.getUsername())
                .updatedBy(user.getUsername())
                .build());
    }

    private DepositCallbackRequest depositCallback(String referenceCode) {
        DepositCallbackRequest request = new DepositCallbackRequest();
        request.setReferenceCode(referenceCode);
        request.setStatus(PaymentOrderStatus.PAID);
        request.setCallbackToken("test-callback-token");
        request.setProviderTransactionId("provider-" + referenceCode);
        return request;
    }

    private CreateWithdrawalRequest withdrawalRequest(String amount) {
        CreateWithdrawalRequest request = new CreateWithdrawalRequest();
        request.setAmount(new BigDecimal(amount));
        request.setBankName("Test Bank");
        request.setBankAccountNumber("123456789");
        request.setBankAccountName("Test User");
        return request;
    }

    private AdminWalletWithdrawalRequest adminWithdrawalRequest(String amount) {
        AdminWalletWithdrawalRequest request = new AdminWalletWithdrawalRequest();
        request.setAmount(new BigDecimal(amount));
        request.setBankName("Admin Bank");
        request.setBankAccountNumber("987654321");
        request.setBankAccountName("Admin Account");
        request.setReason("Operations transfer");
        return request;
    }

    private WithdrawalDecisionRequest decision(String note) {
        WithdrawalDecisionRequest request = new WithdrawalDecisionRequest();
        request.setNote(note);
        return request;
    }

    private Wallet userWallet(User user) {
        return walletRepository.findByUserId(user.getId()).orElseThrow();
    }

    private Wallet adminWallet() {
        return walletRepository.findFirstByOwnerTypeOrderByIdAsc(WalletOwnerType.ADMIN).orElseThrow();
    }
}
