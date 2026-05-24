package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.PaymentOrderRepository;
import com.minhthien.hoser_backend.repository.PasswordResetOtpRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.repository.WithdrawalRequestRepository;
import com.minhthien.hoser_backend.security.JwtTokenProvider;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.PayOsGateway;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:all-api-smoke-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.payment.callback-token=test-callback-token"
})
@AutoConfigureMockMvc
@Import(AllApiSmokeTest.ExternalServiceStubs.class)
class AllApiSmokeTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetOtpRepository otpRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private HorseRepository horseRepository;

    @Autowired
    private JockeyProfileRepository jockeyProfileRepository;

    @Autowired
    private JockeyInvitationRepository jockeyInvitationRepository;

    @Autowired
    private WithdrawalRequestRepository withdrawalRequestRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;
    private User owner;
    private User jockey;
    private User admin;
    private User adminTarget;
    private User deactivationTarget;

    private String userToken;
    private String ownerToken;
    private String jockeyToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        user = createUser("smoke-user", "smoke-user@example.com", UserRole.USER);
        owner = createUser("smoke-owner", "smoke-owner@example.com", UserRole.OWNER);
        jockey = createUser("smoke-jockey", "smoke-jockey@example.com", UserRole.JOCKEY);
        admin = createUser("smoke-admin", "smoke-admin@example.com", UserRole.ADMIN);
        adminTarget = createUser("smoke-target", "smoke-target@example.com", UserRole.USER);
        deactivationTarget = createUser("smoke-deactivate", "smoke-deactivate@example.com", UserRole.USER);
        deactivationTarget.setActive(false);
        userRepository.save(deactivationTarget);

        userToken = token(user);
        ownerToken = token(owner);
        jockeyToken = token(jockey);
        adminToken = token(admin);

        walletService.getOrCreateAdminWallet();
        walletService.credit(user.getId(), new BigDecimal("100000"), WalletTransactionType.DEPOSIT,
                "TEST", "USER-SEED", "smoke:user:seed", null, "Smoke seed");
        walletService.credit(owner.getId(), new BigDecimal("300000"), WalletTransactionType.DEPOSIT,
                "TEST", "OWNER-SEED", "smoke:owner:seed", null, "Smoke seed");
        walletService.creditAdmin(new BigDecimal("300000"), WalletTransactionType.DEPOSIT,
                "TEST", "ADMIN-SEED", "smoke:admin:seed", null, "Smoke seed");
    }

    @Test
    void allControllerApisReturnNonServerErrors() throws Exception {
        exerciseAuthApis();
        exerciseUserAndAdminApis();
        exerciseHorseAndJockeyProfileApis();
        Long horseId = latestHorseId();
        exerciseJockeyInvitationApis(horseId);
        exerciseRaceRegistrationApis();
        exerciseWalletPaymentAndWithdrawalApis();
        exercisePublicWebhookApis();
    }

    private void exerciseAuthApis() throws Exception {
        assertOk(postJson("/api/v1/auth/register", """
                {
                  "username": "smoke-register",
                  "email": "smoke-register@example.com",
                  "password": "Password123!"
                }
                """));

        MvcResult login = assertOk(postJson("/api/v1/auth/login", """
                {
                  "email": "smoke-register@example.com",
                  "password": "Password123!"
                }
                """));
        assertThat(login.getResponse().getContentAsString()).contains("token");

        assertOk(get("/api/v1/auth/me").header("Authorization", bearer(userToken)));
        assertOk(postJson("/api/v1/role-applications/spectator", userToken, """
                {
                  "displayName": "Smoke Fan",
                  "phone": "0900000001",
                  "location": "Ho Chi Minh City",
                  "favoriteHorseBreed": "Thoroughbred",
                  "bio": "Smoke spectator"
                }
                """));
        assertOk(get("/api/v1/role-applications/me").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/admin/role-applications")
                .header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/role-applications?role=SPECTATOR&status=PENDING")
                .header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/role-applications/role/SPECTATOR")
                .header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/role-applications/status/PENDING")
                .header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/auth/password", userToken, """
                {
                  "currentPassword": "Password123!",
                  "newPassword": "Password456!"
                }
                """));
        assertOk(post("/api/v1/auth/logout").header("Authorization", bearer(userToken)));

        assertOk(postJson("/api/v1/auth/forgot-password", """
                {
                  "email": "smoke-register@example.com"
                }
                """));
        String otp = otpRepository.findAll().stream()
                .filter(item -> item.getEmail().equals("smoke-register@example.com"))
                .findFirst()
                .orElseThrow()
                .getOtp();
        assertOk(postJson("/api/v1/auth/reset-password", """
                {
                  "email": "smoke-register@example.com",
                  "otp": "%s",
                  "newPassword": "Password789!"
                }
                """.formatted(otp)));

        assertNonServerError(postJson("/api/v1/auth/google", """
                {
                  "idToken": ""
                }
                """));
        assertNonServerError(postJson("/api/v1/auth/facebook", """
                {
                  "accessToken": ""
                }
                """));
    }

    private void exerciseUserAndAdminApis() throws Exception {
        assertOk(get("/api/v1/users/me/profile").header("Authorization", bearer(ownerToken)));
        assertOk(putJson("/api/v1/users/me/profile", ownerToken, """
                {
                  "fullName": "Smoke Owner",
                  "phone": "0900000000",
                  "location": "Ho Chi Minh City"
                }
                """));

        assertOk(get("/api/v1/admin/users").header("Authorization", bearer(adminToken)));
        MvcResult activeUsers = assertOk(get("/api/v1/admin/users/active")
                .header("Authorization", bearer(adminToken)));
        assertThat(activeUsers.getResponse().getContentAsString()).doesNotContain("\"active\":false");
        MvcResult deactivatedUsers = assertOk(get("/api/v1/admin/users/deactivated")
                .header("Authorization", bearer(adminToken)));
        assertThat(deactivatedUsers.getResponse().getContentAsString())
                .contains("\"active\":false")
                .doesNotContain("\"active\":true");
        assertOk(get("/api/v1/admin/users/" + adminTarget.getId()).header("Authorization", bearer(adminToken)));
        assertOk(put("/api/v1/admin/users/" + adminTarget.getId() + "/deactivate")
                .header("Authorization", bearer(adminToken)));
        assertOk(put("/api/v1/admin/users/" + adminTarget.getId() + "/activate")
                .header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/admin/users/" + adminTarget.getId() + "/role", adminToken, """
                {
                  "role": "OWNER"
                }
                """));

        assertOk(get("/api/v1/admin/audit-logs").header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/finance-settings").header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/admin/finance-settings", adminToken, """
                {
                  "jockeyHireTaxPercent": 10.00
                }
                """));
    }

    private void exerciseHorseAndJockeyProfileApis() throws Exception {
        assertOk(get("/api/v1/horses/approved"));
        assertOk(get("/api/v1/jockeys/available"));
        assertNonServerError(get("/api/v1/jockeys/999999"));

        assertOk(multipart("/api/v1/owner/horses")
                .param("name", "Smoke Horse")
                .param("breed", "Thoroughbred")
                .param("age", "4")
                .param("gender", "MALE")
                .param("color", "Bay")
                .header("Authorization", bearer(ownerToken)));
        Long horseId = latestHorseId();
        assertOk(get("/api/v1/owner/horses").header("Authorization", bearer(ownerToken)));
        assertOk(get("/api/v1/owner/horses/" + horseId).header("Authorization", bearer(ownerToken)));
        assertOk(multipartPut("/api/v1/owner/horses/" + horseId)
                .param("name", "Smoke Horse Updated")
                .param("breed", "Thoroughbred")
                .param("age", "5")
                .param("gender", "MALE")
                .param("color", "Bay")
                .header("Authorization", bearer(ownerToken)));
        assertOk(get("/api/v1/admin/horses").header("Authorization", bearer(adminToken)));
        assertOk(put("/api/v1/admin/horses/" + horseId + "/approve")
                .header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/admin/horses/" + horseId + "/reject", adminToken, """
                {
                  "reason": "Smoke rejection"
                }
                """));
        assertOk(put("/api/v1/admin/horses/" + horseId + "/approve")
                .header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/admin/horses/" + horseId + "/suspend", adminToken, """
                {
                  "reason": "Smoke suspension"
                }
                """));
        assertOk(put("/api/v1/admin/horses/" + horseId + "/approve")
                .header("Authorization", bearer(adminToken)));

        assertNonServerError(get("/api/v1/jockey/profile").header("Authorization", bearer(jockeyToken)));
        assertOk(multipart("/api/v1/jockey/profile")
                .param("licenseNumber", "SMOKE-LICENSE")
                .param("experienceYears", "3")
                .param("hirePrice", "50000")
                .param("bio", "Smoke profile")
                .header("Authorization", bearer(jockeyToken)));
        assertOk(get("/api/v1/jockey/profile").header("Authorization", bearer(jockeyToken)));
        assertOk(multipartPut("/api/v1/jockey/profile")
                .param("licenseNumber", "SMOKE-LICENSE")
                .param("experienceYears", "4")
                .param("hirePrice", "50000")
                .param("bio", "Smoke profile updated")
                .header("Authorization", bearer(jockeyToken)));
        Long profileId = latestJockeyProfileId();
        assertOk(get("/api/v1/admin/jockey-profiles").header("Authorization", bearer(adminToken)));
        approveLatestJockeyProfile();
        assertOk(get("/api/v1/jockeys/" + jockey.getId()));

        assertOk(get("/api/v1/horses/" + horseId));
    }

    private void exerciseJockeyInvitationApis(Long horseId) throws Exception {
        Long firstInvitationId = createInvitation(horseId);
        assertOk(get("/api/v1/owner/jockey-invitations").header("Authorization", bearer(ownerToken)));
        assertOk(get("/api/v1/owner/jockey-invitations/" + firstInvitationId)
                .header("Authorization", bearer(ownerToken)));
        assertOk(put("/api/v1/owner/jockey-invitations/" + firstInvitationId + "/cancel")
                .header("Authorization", bearer(ownerToken)));

        Long secondInvitationId = createInvitation(horseId);
        assertOk(get("/api/v1/jockey/invitations").header("Authorization", bearer(jockeyToken)));
        assertOk(get("/api/v1/jockey/invitations/" + secondInvitationId)
                .header("Authorization", bearer(jockeyToken)));
        assertOk(putJson("/api/v1/jockey/invitations/" + secondInvitationId + "/reject", jockeyToken, """
                {
                  "note": "Smoke reject"
                }
                """));

        Long thirdInvitationId = createInvitation(horseId);
        assertOk(putJson("/api/v1/jockey/invitations/" + thirdInvitationId + "/accept", jockeyToken, """
                {
                  "note": "Smoke accept"
                }
                """));
        assertOk(get("/api/v1/owners/me/jockeys").header("Authorization", bearer(ownerToken)));
    }

    private void exerciseRaceRegistrationApis() throws Exception {
        assertNonServerError(putJson("/api/v1/owner/race-registrations/999999/withdraw", ownerToken, """
                {
                  "note": "Smoke withdraw"
                }
                """));
    }

    private void exerciseWalletPaymentAndWithdrawalApis() throws Exception {
        assertOk(get("/api/v1/wallets/me").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/admin/wallet").header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/wallet/transactions").header("Authorization", bearer(adminToken)));

        assertOk(postJson("/api/v1/wallets/me/deposit-orders", userToken, """
                {
                  "amount": 10000
                }
                """));
        PaymentOrder callbackOrder = latestPaymentOrder();
        assertOk(get("/api/v1/wallets/me/deposit-orders").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/wallets/me/deposit-orders/" + callbackOrder.getId())
                .header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/admin/payment-orders").header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/payment-orders/" + callbackOrder.getId())
                .header("Authorization", bearer(adminToken)));
        assertOk(postJson("/api/v1/payment-callbacks/deposits", """
                {
                  "referenceCode": "%s",
                  "status": "PAID",
                  "callbackToken": "test-callback-token",
                  "providerTransactionId": "SMOKE-CALLBACK"
                }
                """.formatted(callbackOrder.getReferenceCode())));
        assertOk(get("/api/v1/admin/payment-callback-logs").header("Authorization", bearer(adminToken)));

        assertOk(postJson("/api/v1/wallets/me/withdrawals", userToken, """
                {
                  "amount": 1000,
                  "bankName": "Smoke Bank",
                  "bankAccountNumber": "123456789",
                  "bankAccountName": "Smoke User"
                }
                """));
        Long paidWithdrawalId = latestWithdrawalId();
        assertOk(get("/api/v1/wallets/me/withdrawals").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/wallets/me/withdrawals/" + paidWithdrawalId)
                .header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/admin/withdrawals").header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/withdrawals").param("status", "PENDING")
                .header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/withdrawals/" + paidWithdrawalId)
                .header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/admin/withdrawals/" + paidWithdrawalId + "/approve", adminToken, """
                {
                  "note": "Smoke approve"
                }
                """));
        assertOk(putJson("/api/v1/admin/withdrawals/" + paidWithdrawalId + "/mark-paid", adminToken, """
                {
                  "note": "Smoke paid"
                }
                """));

        assertOk(postJson("/api/v1/wallets/me/withdrawals", userToken, """
                {
                  "amount": 1000,
                  "bankName": "Smoke Bank",
                  "bankAccountNumber": "123456789",
                  "bankAccountName": "Smoke User"
                }
                """));
        Long rejectedWithdrawalId = latestWithdrawalId();
        assertOk(putJson("/api/v1/admin/withdrawals/" + rejectedWithdrawalId + "/reject", adminToken, """
                {
                  "note": "Smoke reject"
                }
                """));

        assertOk(postJson("/api/v1/admin/wallet/withdrawals", adminToken, """
                {
                  "amount": 1000,
                  "bankName": "Admin Bank",
                  "bankAccountNumber": "987654321",
                  "bankAccountName": "Smoke Admin",
                  "reason": "Smoke withdrawal"
                }
                """));
        assertOk(get("/api/v1/admin/wallet/withdrawals").header("Authorization", bearer(adminToken)));
    }

    private void exercisePublicWebhookApis() throws Exception {
        assertOk(postJson("/api/v1/wallets/me/deposit-orders", userToken, """
                {
                  "amount": 10000
                }
                """));
        PaymentOrder payOsOrder = latestPaymentOrder();
        String webhook = """
                {
                  "code": "00",
                  "desc": "success",
                  "success": true,
                  "signature": "valid-signature",
                  "data": {
                    "orderCode": %d,
                    "amount": 10000,
                    "description": "HS%d",
                    "accountNumber": "123456789",
                    "paymentLinkId": "%s",
                    "reference": "SMOKE-PAYOS",
                    "transactionDateTime": "2026-05-19 12:00:00",
                    "currency": "VND",
                    "code": "00",
                    "desc": "Thanh cong",
                    "counterAccountBankId": "",
                    "counterAccountBankName": "",
                    "counterAccountName": "",
                    "counterAccountNumber": "",
                    "virtualAccountName": "",
                    "virtualAccountNumber": ""
                  }
                }
                """.formatted(payOsOrder.getOrderCode(), payOsOrder.getOrderCode(), payOsOrder.getPaymentLinkId());
        assertOk(postJson("/api/v1/wallets/top-up/payos/webhook", webhook));

        assertOk(postJson("/api/v1/wallets/me/deposit-orders", userToken, """
                {
                  "amount": 10000
                }
                """));
        PaymentOrder legacyPayOsOrder = latestPaymentOrder();
        String legacyWebhook = webhook.replace(
                String.valueOf(payOsOrder.getOrderCode()),
                String.valueOf(legacyPayOsOrder.getOrderCode())
        ).replace(payOsOrder.getPaymentLinkId(), legacyPayOsOrder.getPaymentLinkId())
                .replace("SMOKE-PAYOS", "SMOKE-PAYOS-LEGACY");
        assertOk(postJson("/api/payos/webhook", legacyWebhook));

        assertOk(put("/api/v1/admin/users/" + deactivationTarget.getId() + "/deactivate")
                .header("Authorization", bearer(adminToken)));
    }

    private Long createInvitation(Long horseId) throws Exception {
        assertOk(postJson("/api/v1/owner/jockey-invitations", ownerToken, """
                {
                  "horseId": %d,
                  "jockeyId": %d,
                  "message": "Smoke invitation"
                }
                """.formatted(horseId, jockey.getId())));
        return latestInvitationId();
    }

    private MvcResult assertOk(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isLessThan(500);
        return result;
    }

    private MvcResult assertNonServerError(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .withFailMessage("Expected non-5xx for %s but got %s with body %s",
                        result.getRequest().getRequestURI(),
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString())
                .isLessThan(500);
        return result;
    }

    private org.springframework.test.web.servlet.RequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.RequestBuilder postJson(String path, String token, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", bearer(token));
    }

    private org.springframework.test.web.servlet.RequestBuilder putJson(String path, String token, String body) {
        return put(path).contentType(MediaType.APPLICATION_JSON).content(body).header("Authorization", bearer(token));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartPut(String path) {
        org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder builder = multipart(path);
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return builder;
    }

    private Long latestHorseId() {
        return horseRepository.findAll().stream()
                .max(Comparator.comparing(horse -> horse.getId()))
                .orElseThrow()
                .getId();
    }

    private Long latestJockeyProfileId() {
        return jockeyProfileRepository.findAll().stream()
                .max(Comparator.comparing(profile -> profile.getId()))
                .orElseThrow()
                .getId();
    }

    private void approveLatestJockeyProfile() {
        var profile = jockeyProfileRepository.findById(latestJockeyProfileId()).orElseThrow();
        profile.setStatus(JockeyStatus.APPROVED);
        profile.setReviewedBy(admin.getId());
        jockeyProfileRepository.save(profile);
    }

    private Long latestInvitationId() {
        return jockeyInvitationRepository.findAll().stream()
                .max(Comparator.comparing(invitation -> invitation.getId()))
                .orElseThrow()
                .getId();
    }

    private Long latestWithdrawalId() {
        return withdrawalRequestRepository.findAll().stream()
                .max(Comparator.comparing(withdrawal -> withdrawal.getId()))
                .orElseThrow()
                .getId();
    }

    private PaymentOrder latestPaymentOrder() {
        return paymentOrderRepository.findAll().stream()
                .max(Comparator.comparing(PaymentOrder::getId))
                .orElseThrow();
    }

    private User createUser(String username, String email, UserRole role) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .active(true)
                .build());
    }

    private String token(User user) {
        return jwtTokenProvider.generateTokenFromUsername(user.getUsername());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class ExternalServiceStubs {
        @Bean
        @Primary
        MailService mailService() {
            return new MailService() {
                @Override
                public void sendOtp(String email, String otp) {
                }

                @Override
                public void sendRoleApplicationApproved(User user, UserRole role) {
                }

                @Override
                public void sendRoleApplicationRejected(User user, UserRole role, String reason) {
                }
            };
        }

        @Bean
        @Primary
        PayOsGateway payOsGateway() {
            return new PayOsGateway() {
                @Override
                public CreatePaymentLinkResponse createPaymentLink(CreatePaymentLinkRequest request) {
                    return CreatePaymentLinkResponse.builder()
                            .bin("970422")
                            .accountNumber("123456789")
                            .accountName("HORSE")
                            .orderCode(request.getOrderCode())
                            .amount(request.getAmount())
                            .description(request.getDescription())
                            .currency("VND")
                            .paymentLinkId("payos-link-" + request.getOrderCode())
                            .status(PaymentLinkStatus.PENDING)
                            .checkoutUrl("https://pay.payos.vn/web/payos-link-" + request.getOrderCode())
                            .qrCode("000201010212")
                            .expiredAt(request.getExpiredAt())
                            .build();
                }

                @Override
                public WebhookData verifyWebhook(Webhook webhook) {
                    if (webhook == null || webhook.getData() == null) {
                        throw new IllegalArgumentException("Missing webhook data");
                    }
                    return webhook.getData();
                }
            };
        }
    }
}
