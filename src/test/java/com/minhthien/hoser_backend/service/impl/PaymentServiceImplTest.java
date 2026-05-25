package com.minhthien.hoser_backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.request.CreateDepositOrderRequest;
import com.minhthien.hoser_backend.dto.response.PaymentOrderResponse;
import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import com.minhthien.hoser_backend.enums.PaymentProvider;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String APP_ID = "2554";
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";
    private static final String CREATE_URL = "https://sb-openapi.zalopay.vn/v2/create";
    private static final String QUERY_URL = "https://sb-openapi.zalopay.vn/v2/query";
    private static final String REDIRECT_URL = "http://localhost:8080/api/zalopay/return";
    private static final String CALLBACK_URL = "http://localhost:8080/api/zalopay/callback";

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private PaymentCallbackLogService paymentCallbackLogService;

    @Mock
    private RestOperations paymentRestOperations;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentOrderRepository,
                userRepository,
                walletService,
                paymentCallbackLogService,
                new ObjectMapper(),
                paymentRestOperations
        );
        ReflectionTestUtils.setField(paymentService, "callbackToken", "test-callback-token");
        ReflectionTestUtils.setField(paymentService, "zaloPayAppId", APP_ID);
        ReflectionTestUtils.setField(paymentService, "zaloPayKey1", KEY1);
        ReflectionTestUtils.setField(paymentService, "zaloPayKey2", KEY2);
        ReflectionTestUtils.setField(paymentService, "zaloPayCreateUrl", CREATE_URL);
        ReflectionTestUtils.setField(paymentService, "zaloPayQueryUrl", QUERY_URL);
        ReflectionTestUtils.setField(paymentService, "zaloPayRedirectUrl", REDIRECT_URL);
        ReflectionTestUtils.setField(paymentService, "zaloPayCallbackUrl", CALLBACK_URL);
    }

    @Test
    void createZaloPayDepositOrderReturnsCheckoutUrlAndSignsRequest() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> {
            PaymentOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(10L);
            }
            return order;
        });
        when(paymentRestOperations.postForEntity(eq(CREATE_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "return_code", 1,
                        "return_message", "success",
                        "order_url", "https://sandbox.zalopay.vn/order/10",
                        "qr_code", "QR10"
                )));

        PaymentOrderResponse response = paymentService.createDepositOrder(1L, depositRequest("10000"));

        String appTransId = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")) + "_10";
        assertThat(response.getProvider()).isEqualTo(PaymentProvider.ZALOPAY);
        assertThat(response.getOrderCode()).isEqualTo(10L);
        assertThat(response.getPaymentLinkId()).isEqualTo(appTransId);
        assertThat(response.getCheckoutUrl()).isEqualTo("https://sandbox.zalopay.vn/order/10");
        assertThat(response.getQrCode()).isEqualTo("QR10");

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(paymentRestOperations).postForEntity(eq(CREATE_URL), captor.capture(), eq(Map.class));
        MultiValueMap<String, String> body = (MultiValueMap<String, String>) captor.getValue().getBody();
        assertThat(body.getFirst("app_id")).isEqualTo(APP_ID);
        assertThat(body.getFirst("app_trans_id")).isEqualTo(appTransId);
        assertThat(body.getFirst("amount")).isEqualTo("10000");
        assertThat(body.getFirst("callback_url")).isEqualTo(CALLBACK_URL);
        assertThat(body.getFirst("redirect_url")).isEqualTo(REDIRECT_URL);
        String macData = APP_ID + "|" + appTransId + "|" + user.getUsername() + "|10000|"
                + body.getFirst("app_time") + "|{}|[]";
        assertThat(body.getFirst("mac")).isEqualTo(hmac(KEY1, macData));
    }

    @Test
    void invalidZaloPayCallbackMacDoesNotCreditWallets() {
        Map<String, Object> response = paymentService.handleZaloPayCallback(Map.of(
                "data", "{\"app_trans_id\":\"260525_10\"}",
                "mac", "bad-mac",
                "type", 1
        ));

        assertThat(response).containsEntry("return_code", 2);
        verify(walletService, never()).credit(any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletService, never()).creditAdmin(any(), any(), any(), any(), any(), any(), any());
        verify(paymentCallbackLogService).record(any(), eq(false), eq(false), eq("Invalid ZaloPay callback mac"));
    }

    @Test
    void paidZaloPayCallbackCreditsWalletsAndDuplicateDoesNotDoubleCredit() {
        User user = user();
        PaymentOrder order = zaloPayOrder(user);
        when(paymentOrderRepository.findByPaymentLinkId(order.getPaymentLinkId())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> paid = paymentService.handleZaloPayCallback(callbackPayload(order));
        Map<String, Object> duplicate = paymentService.handleZaloPayCallback(callbackPayload(order));

        assertThat(paid).containsEntry("return_code", 1);
        assertThat(duplicate).containsEntry("return_code", 1);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verify(walletService, times(1)).credit(eq(1L), eq(new BigDecimal("10000")),
                eq(WalletTransactionType.DEPOSIT), eq("DEPOSIT_ORDER"), eq(order.getReferenceCode()),
                eq("deposit:user:" + order.getReferenceCode()), any(), eq("ZaloPay callback paid"));
        verify(walletService, times(1)).creditAdmin(eq(new BigDecimal("10000")),
                eq(WalletTransactionType.DEPOSIT), eq("DEPOSIT_ORDER"), eq(order.getReferenceCode()),
                eq("deposit:admin:" + order.getReferenceCode()), any(), eq("ZaloPay callback paid"));
    }

    @Test
    void zaloPayReturnVerifiesChecksumQueriesAndCreditsWallets() {
        User user = user();
        PaymentOrder order = zaloPayOrder(user);
        when(paymentOrderRepository.findByPaymentLinkId(order.getPaymentLinkId())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRestOperations.postForEntity(eq(QUERY_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "return_code", 1,
                        "return_message", "success",
                        "zp_trans_id", 123456789L,
                        "amount", 10000
                )));

        Map<String, Object> response = paymentService.handleZaloPayReturn(signedReturnParams(order));

        assertThat(response).containsEntry("return_code", 1);
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getProviderTransactionId()).isEqualTo("123456789");
        verify(walletService, times(1)).credit(eq(1L), eq(new BigDecimal("10000")),
                eq(WalletTransactionType.DEPOSIT), eq("DEPOSIT_ORDER"), eq(order.getReferenceCode()),
                eq("deposit:user:" + order.getReferenceCode()), any(), eq("ZaloPay query paid"));
    }

    @Test
    void createDepositOrderRejectsFractionalVndBeforeCallingZaloPay() {
        CreateDepositOrderRequest request = depositRequest("10000.50");

        assertThatThrownBy(() -> paymentService.createDepositOrder(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be a whole VND amount");

        verifyNoInteractions(userRepository, paymentOrderRepository, paymentRestOperations);
    }

    private CreateDepositOrderRequest depositRequest(String amount) {
        CreateDepositOrderRequest request = new CreateDepositOrderRequest();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("zalopay-user")
                .email("zalopay-user@example.com")
                .build();
    }

    private PaymentOrder zaloPayOrder(User user) {
        return PaymentOrder.builder()
                .id(10L)
                .user(user)
                .amount(new BigDecimal("10000"))
                .currency(PaymentOrder.DEFAULT_CURRENCY)
                .provider(PaymentProvider.ZALOPAY)
                .status(PaymentOrderStatus.PENDING)
                .referenceCode("DEP-TESTZALOPAY")
                .orderCode(10L)
                .paymentLinkId("260525_10")
                .build();
    }

    private Map<String, Object> callbackPayload(PaymentOrder order) {
        String data = """
                {"app_id":2554,"app_trans_id":"%s","app_time":1779690000000,"app_user":"zalopay-user","amount":10000,"embed_data":"{}","item":"[]","zp_trans_id":123456789}
                """.formatted(order.getPaymentLinkId()).trim();
        return Map.of("data", data, "mac", hmac(KEY2, data), "type", 1);
    }

    private Map<String, String> signedReturnParams(PaymentOrder order) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("appid", APP_ID);
        params.put("apptransid", order.getPaymentLinkId());
        params.put("pmcid", "38");
        params.put("bankcode", "");
        params.put("amount", "10000");
        params.put("discountamount", "0");
        params.put("status", "1");
        String checksumData = String.join("|",
                params.get("appid"),
                params.get("apptransid"),
                params.get("pmcid"),
                params.get("bankcode"),
                params.get("amount"),
                params.get("discountamount"),
                params.get("status"));
        params.put("checksum", hmac(KEY2, checksumData));
        return params;
    }

    private String hmac(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
