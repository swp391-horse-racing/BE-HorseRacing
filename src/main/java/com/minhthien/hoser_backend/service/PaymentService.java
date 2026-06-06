package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.CreateDepositOrderRequest;
import com.minhthien.hoser_backend.dto.request.DepositCallbackRequest;
import com.minhthien.hoser_backend.dto.response.PaymentCallbackLogResponse;
import com.minhthien.hoser_backend.dto.response.PaymentOrderResponse;

import java.util.List;
import java.util.Map;

public interface PaymentService {
    PaymentOrderResponse createDepositOrder(Long userId, CreateDepositOrderRequest request);

    PaymentOrderResponse createAdminWalletDepositOrder(Long adminId, CreateDepositOrderRequest request);

    List<PaymentOrderResponse> getUserDepositOrders(Long userId);

    PaymentOrderResponse getUserDepositOrder(Long userId, Long orderId);

    List<PaymentOrderResponse> getAdminWalletDepositOrders();

    PaymentOrderResponse getAdminWalletDepositOrder(Long orderId);

    List<PaymentOrderResponse> getAdminPaymentOrders();

    PaymentOrderResponse getAdminPaymentOrder(Long orderId);

    List<PaymentCallbackLogResponse> getAdminPaymentCallbackLogs();

    PaymentOrderResponse handleDepositCallback(DepositCallbackRequest request);

    Map<String, Object> handleZaloPayReturn(Map<String, String> params);

    Map<String, Object> handleZaloPayCallback(Map<String, Object> payload);
}
