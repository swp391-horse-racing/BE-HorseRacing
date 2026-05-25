package com.minhthien.hoser_backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.request.CreateDepositOrderRequest;
import com.minhthien.hoser_backend.dto.request.DepositCallbackRequest;
import com.minhthien.hoser_backend.dto.response.PaymentCallbackLogResponse;
import com.minhthien.hoser_backend.dto.response.PaymentOrderResponse;
import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.PaymentOrderStatus;
import com.minhthien.hoser_backend.enums.PaymentProvider;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.PaymentOrderRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.PaymentCallbackLogService;
import com.minhthien.hoser_backend.service.PaymentService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String REFERENCE_TYPE = "DEPOSIT_ORDER";
    private static final DateTimeFormatter ZALOPAY_TRANS_DATE = DateTimeFormatter.ofPattern("yyMMdd");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PaymentCallbackLogService paymentCallbackLogService;
    private final ObjectMapper objectMapper;
    private final RestOperations paymentRestOperations;

    @Value("${app.payment.callback-token:dev-callback-token}")
    private String callbackToken;

    @Value("${zalopay.app-id}")
    private String zaloPayAppId;

    @Value("${zalopay.key1}")
    private String zaloPayKey1;

    @Value("${zalopay.key2}")
    private String zaloPayKey2;

    @Value("${zalopay.create-url}")
    private String zaloPayCreateUrl;

    @Value("${zalopay.query-url}")
    private String zaloPayQueryUrl;

    @Value("${zalopay.redirect-url}")
    private String zaloPayRedirectUrl;

    @Value("${zalopay.callback-url}")
    private String zaloPayCallbackUrl;

    @Override
    @Transactional
    public PaymentOrderResponse createDepositOrder(Long userId, CreateDepositOrderRequest request) {
        validateAmount(request.getAmount());
        validateCurrency(request.getCurrency());
        PaymentProvider provider = resolveProvider(request.getProvider());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        String referenceCode = "DEP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .amount(request.getAmount())
                .currency(PaymentOrder.DEFAULT_CURRENCY)
                .provider(provider)
                .status(PaymentOrderStatus.PENDING)
                .referenceCode(referenceCode)
                .transferContent("HORSE " + referenceCode)
                .expiredAt(LocalDateTime.now().plusMinutes(30))
                .createdBy(user.getUsername())
                .updatedBy(user.getUsername())
                .build();
        PaymentOrder savedOrder = paymentOrderRepository.save(order);

        Long orderCode = savedOrder.getId();
        String appTransId = buildZaloPayAppTransId(orderCode);
        String description = buildZaloPayDescription(orderCode);
        Map<String, Object> zaloPayResponse = createZaloPayOrder(savedOrder, user, appTransId, description);
        String checkoutUrl = asString(zaloPayResponse.get("order_url"));
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new BadRequestException("ZaloPay did not return an order URL");
        }

        savedOrder.setOrderCode(orderCode);
        savedOrder.setPaymentLinkId(appTransId);
        savedOrder.setCheckoutUrl(checkoutUrl);
        savedOrder.setQrCode(asString(zaloPayResponse.get("qr_code")));
        savedOrder.setTransferContent(description);
        savedOrder.setMetadata(toMetadata(zaloPayResponse));
        return mapToResponse(paymentOrderRepository.save(savedOrder));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> getUserDepositOrders(Long userId) {
        return paymentOrderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse getUserDepositOrder(Long userId, Long orderId) {
        return paymentOrderRepository.findByIdAndUserId(orderId, userId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", "id", orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> getAdminPaymentOrders() {
        return paymentOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentOrderResponse getAdminPaymentOrder(Long orderId) {
        return paymentOrderRepository.findById(orderId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", "id", orderId));
    }

    @Override
    public List<PaymentCallbackLogResponse> getAdminPaymentCallbackLogs() {
        return paymentCallbackLogService.getAdminPaymentCallbackLogs();
    }

    @Override
    @Transactional
    public PaymentOrderResponse handleDepositCallback(DepositCallbackRequest request) {
        if (!isValidCallbackToken(request.getCallbackToken())) {
            paymentCallbackLogService.record(request, false, false, "Invalid payment callback token");
            throw new BadRequestException("Invalid payment callback token");
        }

        try {
            PaymentOrderResponse response = processDepositCallback(request);
            paymentCallbackLogService.record(request, true, true, null);
            return response;
        } catch (RuntimeException ex) {
            paymentCallbackLogService.record(request, true, false, ex.getMessage());
            throw ex;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> handleZaloPayReturn(Map<String, String> params) {
        String appTransId = params.get("apptransid");
        DepositCallbackRequest logRequest = toZaloPayLogRequest(appTransId, null, PaymentOrderStatus.FAILED, params);
        if (!isValidZaloPayRedirect(params)) {
            paymentCallbackLogService.record(logRequest, false, false, "Invalid ZaloPay redirect checksum");
            return response("return_code", 2, "return_message", "Invalid checksum");
        }
        Map<String, Object> queryResponse = queryZaloPayOrder(appTransId);
        return processZaloPayQueryResponse(appTransId, queryResponse);
    }

    @Override
    @Transactional
    public Map<String, Object> handleZaloPayCallback(Map<String, Object> payload) {
        String data = asString(payload.get("data"));
        String mac = asString(payload.get("mac"));
        if (data == null || mac == null || !hmacSha256(zaloPayKey2, data).equalsIgnoreCase(mac)) {
            paymentCallbackLogService.record(toZaloPayLogRequest(null, null, PaymentOrderStatus.FAILED, payload),
                    false, false, "Invalid ZaloPay callback mac");
            return response("return_code", 2, "return_message", "Invalid");
        }

        try {
            Map<String, Object> dataMap = objectMapper.readValue(data, MAP_TYPE);
            String appTransId = asString(dataMap.get("app_trans_id"));
            String providerTransactionId = asString(dataMap.get("zp_trans_id"));
            BigDecimal amount = toBigDecimal(dataMap.get("amount"));
            processZaloPayPaid(appTransId, providerTransactionId, amount, dataMap, "ZaloPay callback paid");
            return response("return_code", 1, "return_message", "success");
        } catch (RuntimeException | JsonProcessingException ex) {
            paymentCallbackLogService.record(toZaloPayLogRequest(null, null, PaymentOrderStatus.FAILED, payload),
                    true, false, ex.getMessage());
            return response("return_code", 0, "return_message", ex.getMessage());
        }
    }

    private PaymentOrderResponse processDepositCallback(DepositCallbackRequest request) {
        PaymentOrder order = paymentOrderRepository.findByReferenceCode(request.getReferenceCode())
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", "referenceCode", request.getReferenceCode()));

        if (order.getStatus() == PaymentOrderStatus.PAID) {
            return mapToResponse(order);
        }
        if (order.getStatus() == PaymentOrderStatus.CANCELLED || order.getStatus() == PaymentOrderStatus.EXPIRED) {
            throw new BadRequestException("Payment order cannot be paid from status " + order.getStatus());
        }
        if (request.getStatus() == PaymentOrderStatus.FAILED || request.getStatus() == PaymentOrderStatus.CANCELLED) {
            order.setStatus(request.getStatus());
            order.setProviderTransactionId(request.getProviderTransactionId());
            order.setMetadata(request.getMetadata());
            return mapToResponse(paymentOrderRepository.save(order));
        }
        if (request.getStatus() != PaymentOrderStatus.PAID) {
            throw new BadRequestException("Unsupported callback status: " + request.getStatus());
        }

        String referenceId = order.getReferenceCode();
        walletService.credit(order.getUser().getId(), order.getAmount(), WalletTransactionType.DEPOSIT,
                REFERENCE_TYPE, referenceId, "deposit:user:" + referenceId, request.getMetadata(), "Deposit paid");
        walletService.creditAdmin(order.getAmount(), WalletTransactionType.DEPOSIT,
                REFERENCE_TYPE, referenceId, "deposit:admin:" + referenceId, request.getMetadata(), "Deposit paid");

        order.setStatus(PaymentOrderStatus.PAID);
        order.setProviderTransactionId(request.getProviderTransactionId());
        order.setMetadata(request.getMetadata());
        order.setPaidAt(LocalDateTime.now());
        return mapToResponse(paymentOrderRepository.save(order));
    }

    private Map<String, Object> createZaloPayOrder(PaymentOrder order, User user, String appTransId, String description) {
        String appUser = user.getUsername() == null || user.getUsername().isBlank() ? "hoser" : user.getUsername();
        long amount = order.getAmount().longValueExact();
        long appTime = System.currentTimeMillis();
        String item = "[]";
        String embedData = "{}";
        String macData = String.join("|",
                zaloPayAppId,
                appTransId,
                appUser,
                String.valueOf(amount),
                String.valueOf(appTime),
                embedData,
                item);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("app_id", zaloPayAppId);
        body.add("app_trans_id", appTransId);
        body.add("app_user", appUser);
        body.add("app_time", String.valueOf(appTime));
        body.add("amount", String.valueOf(amount));
        body.add("item", item);
        body.add("embed_data", embedData);
        body.add("description", description);
        body.add("callback_url", zaloPayCallbackUrl);
        body.add("redirect_url", zaloPayRedirectUrl);
        body.add("mac", hmacSha256(zaloPayKey1, macData));

        Map<String, Object> response = postForm(zaloPayCreateUrl, body);
        if (toInt(response.get("return_code")) != 1) {
            throw new BadRequestException("Could not create ZaloPay order: "
                    + response.getOrDefault("return_message", response));
        }
        return response;
    }

    private Map<String, Object> queryZaloPayOrder(String appTransId) {
        if (appTransId == null || appTransId.isBlank()) {
            throw new BadRequestException("ZaloPay transaction reference is required");
        }
        String macData = zaloPayAppId + "|" + appTransId + "|" + zaloPayKey1;
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("app_id", zaloPayAppId);
        body.add("app_trans_id", appTransId);
        body.add("mac", hmacSha256(zaloPayKey1, macData));
        return postForm(zaloPayQueryUrl, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postForm(String url, MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> response = paymentRestOperations.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        if (response.getBody() == null) {
            throw new BadRequestException("Empty ZaloPay response");
        }
        return new LinkedHashMap<>((Map<String, Object>) response.getBody());
    }

    private Map<String, Object> processZaloPayQueryResponse(String appTransId, Map<String, Object> queryResponse) {
        int returnCode = toInt(queryResponse.get("return_code"));
        if (returnCode == 1) {
            String providerTransactionId = asString(queryResponse.get("zp_trans_id"));
            BigDecimal amount = toBigDecimal(queryResponse.get("amount"));
            processZaloPayPaid(appTransId, providerTransactionId, amount, queryResponse, "ZaloPay query paid");
        } else if (returnCode == 2) {
            processZaloPayFailed(appTransId, queryResponse);
        }
        return queryResponse;
    }

    private void processZaloPayPaid(String appTransId,
                                    String providerTransactionId,
                                    BigDecimal amount,
                                    Object metadata,
                                    String note) {
        PaymentOrder order = findZaloPayOrder(appTransId);
        DepositCallbackRequest logRequest = toZaloPayLogRequest(appTransId, providerTransactionId, PaymentOrderStatus.PAID, metadata);
        if (amount != null && order.getAmount().compareTo(amount) != 0) {
            paymentCallbackLogService.record(logRequest, true, false, "Invalid amount");
            throw new BadRequestException("Invalid amount");
        }
        if (order.getStatus() == PaymentOrderStatus.PAID) {
            paymentCallbackLogService.record(logRequest, true, true, null);
            return;
        }
        if (order.getStatus() == PaymentOrderStatus.CANCELLED || order.getStatus() == PaymentOrderStatus.EXPIRED) {
            paymentCallbackLogService.record(logRequest, true, false, "Order cannot be updated from status " + order.getStatus());
            return;
        }

        String metadataJson = toMetadata(metadata);
        String referenceId = order.getReferenceCode();
        walletService.credit(order.getUser().getId(), order.getAmount(), WalletTransactionType.DEPOSIT,
                REFERENCE_TYPE, referenceId, "deposit:user:" + referenceId, metadataJson, note);
        walletService.creditAdmin(order.getAmount(), WalletTransactionType.DEPOSIT,
                REFERENCE_TYPE, referenceId, "deposit:admin:" + referenceId, metadataJson, note);

        order.setStatus(PaymentOrderStatus.PAID);
        order.setProviderTransactionId(providerTransactionId);
        order.setMetadata(metadataJson);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);
        paymentCallbackLogService.record(logRequest, true, true, null);
    }

    private void processZaloPayFailed(String appTransId, Object metadata) {
        PaymentOrder order = findZaloPayOrder(appTransId);
        DepositCallbackRequest logRequest = toZaloPayLogRequest(appTransId, null, PaymentOrderStatus.FAILED, metadata);
        if (order.getStatus() == PaymentOrderStatus.PENDING) {
            order.setStatus(PaymentOrderStatus.FAILED);
            order.setMetadata(toMetadata(metadata));
            paymentOrderRepository.save(order);
        }
        paymentCallbackLogService.record(logRequest, true, true, null);
    }

    private PaymentOrder findZaloPayOrder(String appTransId) {
        return paymentOrderRepository.findByPaymentLinkId(appTransId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentOrder", "appTransId", appTransId));
    }

    private boolean isValidZaloPayRedirect(Map<String, String> params) {
        String checksum = params.get("checksum");
        if (checksum == null || checksum.isBlank()) {
            return false;
        }
        String checksumData = String.join("|",
                nullToEmpty(params.get("appid")),
                nullToEmpty(params.get("apptransid")),
                nullToEmpty(params.get("pmcid")),
                nullToEmpty(params.get("bankcode")),
                nullToEmpty(params.get("amount")),
                nullToEmpty(params.get("discountamount")),
                nullToEmpty(params.get("status")));
        return hmacSha256(zaloPayKey2, checksumData).equalsIgnoreCase(checksum);
    }

    private DepositCallbackRequest toZaloPayLogRequest(String appTransId,
                                                       String providerTransactionId,
                                                       PaymentOrderStatus status,
                                                       Object metadata) {
        DepositCallbackRequest request = new DepositCallbackRequest();
        request.setReferenceCode(appTransId == null || appTransId.isBlank() ? "ZALOPAY_UNKNOWN" : appTransId);
        request.setStatus(status);
        request.setCallbackToken("ZALOPAY_SIGNATURE");
        request.setProviderTransactionId(providerTransactionId);
        request.setMetadata(toMetadata(metadata));
        return request;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        try {
            amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Amount must be a whole VND amount");
        }
    }

    private void validateCurrency(String currency) {
        if (currency != null && !currency.isBlank()
                && !PaymentOrder.DEFAULT_CURRENCY.equalsIgnoreCase(currency)) {
            throw new BadRequestException("Only VND currency is supported");
        }
    }

    private PaymentProvider resolveProvider(PaymentProvider provider) {
        if (provider == null) {
            return PaymentProvider.ZALOPAY;
        }
        if (provider != PaymentProvider.ZALOPAY) {
            throw new BadRequestException("Only ZALOPAY provider is supported");
        }
        return provider;
    }

    private boolean isValidCallbackToken(String token) {
        return callbackToken != null && !callbackToken.isBlank() && callbackToken.equals(token);
    }

    private String buildZaloPayAppTransId(Long orderCode) {
        return LocalDate.now().format(ZALOPAY_TRANS_DATE) + "_" + orderCode;
    }

    private String buildZaloPayDescription(Long orderCode) {
        return "HOSER deposit order " + orderCode;
    }

    private String hmacSha256(String key, String data) {
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
            throw new BadRequestException("Could not sign ZaloPay request");
        }
    }

    private Map<String, Object> response(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(key1, value1);
        response.put(key2, value2);
        return response;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return new BigDecimal(value.toString());
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String toMetadata(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return value.toString();
        }
    }

    private PaymentOrderResponse mapToResponse(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .amount(order.getAmount())
                .currency(order.getCurrency())
                .provider(order.getProvider())
                .status(order.getStatus())
                .referenceCode(order.getReferenceCode())
                .providerTransactionId(order.getProviderTransactionId())
                .orderCode(order.getOrderCode())
                .paymentLinkId(order.getPaymentLinkId())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCode(order.getQrCode())
                .transferContent(order.getTransferContent())
                .paidAt(order.getPaidAt())
                .expiredAt(order.getExpiredAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
