package com.minhthien.hoser_backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.request.CreateDepositOrderRequest;
import com.minhthien.hoser_backend.dto.request.DepositCallbackRequest;
import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.PaymentDepositTarget;
import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import com.minhthien.hoser_backend.enums.PaymentProvider;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.repository.PaymentOrderRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.PaymentCallbackLogService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private PaymentCallbackLogService paymentCallbackLogService;
    @Mock private RestOperations paymentRestOperations;

    private PaymentServiceImpl paymentService;
    private User admin;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentOrderRepository,
                userRepository,
                walletService,
                paymentCallbackLogService,
                new ObjectMapper(),
                paymentRestOperations);
        ReflectionTestUtils.setField(paymentService, "callbackToken", "test-token");
        ReflectionTestUtils.setField(paymentService, "zaloPayAppId", "2554");
        ReflectionTestUtils.setField(paymentService, "zaloPayKey1", "key-1");
        ReflectionTestUtils.setField(paymentService, "zaloPayCreateUrl", "https://zalopay.test/create");
        ReflectionTestUtils.setField(paymentService, "zaloPayRedirectUrl", "https://app.test/return");
        ReflectionTestUtils.setField(paymentService, "zaloPayCallbackUrl", "https://app.test/callback");

        admin = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .password("password")
                .role(UserRole.ADMIN)
                .build();
    }

    @Test
    void createAdminWalletDepositOrderSetsAdminTarget() {
        CreateDepositOrderRequest request = depositRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> {
            PaymentOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(10L);
            }
            return order;
        });
        when(paymentRestOperations.postForEntity(anyString(), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "return_code", 1,
                        "order_url", "https://zalopay.test/checkout",
                        "qr_code", "qr-value")));

        var response = paymentService.createAdminWalletDepositOrder(1L, request);

        assertThat(response.getDepositTarget()).isEqualTo(PaymentDepositTarget.ADMIN_WALLET);
        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDepositTarget()).isEqualTo(PaymentDepositTarget.ADMIN_WALLET);
    }

    @Test
    void paidAdminDepositCreditsOnlyAdminWallet() {
        PaymentOrder order = paidCandidate(PaymentDepositTarget.ADMIN_WALLET);
        when(paymentOrderRepository.findByReferenceCode("DEP-ADMIN")).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(order)).thenReturn(order);

        paymentService.handleDepositCallback(callback("DEP-ADMIN"));

        verify(walletService, never()).credit(anyLong(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService).creditAdmin(
                eq(new BigDecimal("1000000")),
                eq(WalletTransactionType.DEPOSIT),
                eq("DEPOSIT_ORDER"),
                eq("DEP-ADMIN"),
                eq("deposit:admin:DEP-ADMIN"),
                eq("{\"source\":\"test\"}"),
                eq("Deposit paid"));
    }

    @Test
    void paidUserDepositKeepsExistingDualCreditBehavior() {
        PaymentOrder order = paidCandidate(PaymentDepositTarget.USER_WALLET);
        order.setReferenceCode("DEP-USER");
        when(paymentOrderRepository.findByReferenceCode("DEP-USER")).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(order)).thenReturn(order);

        paymentService.handleDepositCallback(callback("DEP-USER"));

        verify(walletService).credit(
                eq(1L),
                eq(new BigDecimal("1000000")),
                eq(WalletTransactionType.DEPOSIT),
                eq("DEPOSIT_ORDER"),
                eq("DEP-USER"),
                eq("deposit:user:DEP-USER"),
                eq("{\"source\":\"test\"}"),
                eq("Deposit paid"));
        verify(walletService).creditAdmin(
                eq(new BigDecimal("1000000")),
                eq(WalletTransactionType.DEPOSIT),
                eq("DEPOSIT_ORDER"),
                eq("DEP-USER"),
                eq("deposit:admin:DEP-USER"),
                eq("{\"source\":\"test\"}"),
                eq("Deposit paid"));
    }

    @Test
    void repeatedPaidCallbackDoesNotCreditWalletAgain() {
        PaymentOrder order = paidCandidate(PaymentDepositTarget.ADMIN_WALLET);
        order.setStatus(PaymentOrderStatus.PAID);
        when(paymentOrderRepository.findByReferenceCode("DEP-ADMIN")).thenReturn(Optional.of(order));

        paymentService.handleDepositCallback(callback("DEP-ADMIN"));

        verifyNoInteractions(walletService);
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    void adminWalletDepositHistoryUsesAdminTargetFilter() {
        PaymentOrder order = paidCandidate(PaymentDepositTarget.ADMIN_WALLET);
        when(paymentOrderRepository.findByDepositTargetOrderByCreatedAtDesc(
                PaymentDepositTarget.ADMIN_WALLET)).thenReturn(List.of(order));

        var response = paymentService.getAdminWalletDepositOrders();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getDepositTarget()).isEqualTo(PaymentDepositTarget.ADMIN_WALLET);
    }

    private CreateDepositOrderRequest depositRequest() {
        CreateDepositOrderRequest request = new CreateDepositOrderRequest();
        request.setAmount(new BigDecimal("1000000"));
        request.setCurrency("VND");
        request.setProvider(PaymentProvider.ZALOPAY);
        return request;
    }

    private PaymentOrder paidCandidate(PaymentDepositTarget target) {
        return PaymentOrder.builder()
                .id(10L)
                .user(admin)
                .amount(new BigDecimal("1000000"))
                .currency("VND")
                .provider(PaymentProvider.ZALOPAY)
                .status(PaymentOrderStatus.PENDING)
                .depositTarget(target)
                .referenceCode("DEP-ADMIN")
                .expiredAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    private DepositCallbackRequest callback(String referenceCode) {
        DepositCallbackRequest request = new DepositCallbackRequest();
        request.setReferenceCode(referenceCode);
        request.setProviderTransactionId("ZP-123");
        request.setStatus(PaymentOrderStatus.PAID);
        request.setCallbackToken("test-token");
        request.setMetadata("{\"source\":\"test\"}");
        return request;
    }
}
