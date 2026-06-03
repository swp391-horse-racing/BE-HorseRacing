package com.minhthien.hoser_backend.controller;

import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.PaymentOrder;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.NewsArticleRepository;
import com.minhthien.hoser_backend.repository.NotificationRepository;
import com.minhthien.hoser_backend.repository.PaymentOrderRepository;
import com.minhthien.hoser_backend.repository.PasswordResetOtpRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.repository.WithdrawalRequestRepository;
import com.minhthien.hoser_backend.security.JwtTokenProvider;
import com.minhthien.hoser_backend.service.MailService;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        "app.payment.callback-token=test-callback-token",
        "zalopay.app-id=2554",
        "zalopay.key1=test-key1",
        "zalopay.key2=test-key2",
        "zalopay.create-url=https://sb-openapi.zalopay.vn/v2/create",
        "zalopay.query-url=https://sb-openapi.zalopay.vn/v2/query",
        "zalopay.redirect-url=http://localhost:8080/api/zalopay/return",
        "zalopay.callback-url=http://localhost:8080/api/zalopay/callback"
})
@AutoConfigureMockMvc
@Import(AllApiSmokeTest.ExternalServiceStubs.class)
class AllApiSmokeTest {

    private static final String PASSWORD = "Password123!";
    private static final String ZALOPAY_KEY2 = "test-key2";

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
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

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
        exerciseNewsApis();
        exerciseHorseAndJockeyProfileApis();
        Long horseId = latestHorseId();
        exerciseJockeyInvitationApis(horseId);
        exerciseRaceRegistrationApis();
        exerciseTournamentApis();
        exerciseRaceSchedulingApis();
        exercisePhase9RaceOperationApis();
        exerciseWalletPaymentAndWithdrawalApis();
        exercisePublicWebhookApis();
        exerciseNotificationApis();
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
        MvcResult spectatorMe = assertOk(get("/api/v1/auth/me").header("Authorization", bearer(userToken)));
        assertThat(spectatorMe.getResponse().getContentAsString()).contains("\"role\":\"SPECTATOR\"");
        assertOk(get("/api/v1/role-applications/me").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/admin/role-applications")
                .header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/role-applications?role=SPECTATOR&status=APPROVED")
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
                  "jockeyHireTaxPercent": 10.00,
                  "bettingEnabled": true
                }
                """));
    }

    private void exerciseNewsApis() throws Exception {
        assertOk(postJson("/api/v1/admin/news", adminToken, """
                {
                  "title": "Smoke Race News",
                  "summary": "Smoke news summary",
                  "content": "Smoke news content",
                  "category": "Su kien",
                  "featured": true,
                  "publishedAt": "2026-05-20T08:00:00"
                }
                """));
        assertOk(multipart("/api/v1/admin/news")
                .param("title", "Smoke Multipart Race News")
                .param("summary", "Smoke multipart news summary")
                .param("content", "Smoke multipart news content")
                .param("category", "Su kien")
                .param("featured", "false")
                .param("publishedAt", "2026-05-21T08:00:00")
                .header("Authorization", bearer(adminToken)));
        Long newsId = latestNewsId();

        assertOk(get("/api/v1/news"));
        assertOk(get("/api/v1/news/all"));
        assertOk(get("/api/v1/news?featured=true"));
        assertOk(get("/api/v1/news?category=Su%20kien"));
        assertOk(get("/api/v1/news/" + newsId));
        assertOk(get("/api/v1/admin/news").header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/news/" + newsId).header("Authorization", bearer(adminToken)));
        assertOk(putJson("/api/v1/admin/news/" + newsId, adminToken, """
                {
                  "title": "Smoke Race News Updated",
                  "summary": "Smoke news summary updated",
                  "content": "Smoke news content updated",
                  "featured": false
                }
                """));
        assertOk(multipartPut("/api/v1/admin/news/" + newsId)
                .param("title", "Smoke Multipart Race News Updated")
                .param("summary", "Smoke multipart news summary updated")
                .param("content", "Smoke multipart news content updated")
                .param("featured", "true")
                .header("Authorization", bearer(adminToken)));
        assertOk(delete("/api/v1/admin/news/" + newsId)
                .header("Authorization", bearer(adminToken)));
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
        assertNonServerError(delete("/api/v1/owner/horses/999999")
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

        seedJockeyProfile();
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

    private void exerciseTournamentApis() throws Exception {
        assertNonServerError(multipart("/api/v1/admin/tournament-banners")
                .file(new MockMultipartFile("banner", "banner.jpg", "image/jpeg", "img".getBytes()))
                .header("Authorization", bearer(userToken)));
        assertOk(postJson("/api/v1/admin/tournaments", adminToken, """
                {
                  "name": "Smoke Tournament",
                  "description": "Smoke tournament",
                  "location": "Ho Chi Minh City",
                  "bannerUrl": "https://cdn.example/tournaments/smoke-banner.jpg",
                  "registrationOpenAt": "2026-06-01T08:00:00",
                  "registrationCloseAt": "2026-06-02T08:00:00",
                  "startAt": "2026-06-03T08:00:00",
                  "endAt": "2026-06-03T18:00:00",
                  "checkInDeadlineAt": "2026-06-03T07:30:00",
                  "minTeams": 1,
                  "maxTeams": 8,
                  "jockeyChallengeEnabled": true,
                  "jockeyChallengeFirstPoints": 3,
                  "jockeyChallengeSecondPoints": 2,
                  "jockeyChallengeThirdPoints": 1,
                  "jockeyChallengePrizes": []
                }
                """));
        assertNonServerError(multipartPut("/api/v1/admin/tournaments/999999/banner")
                .file(new MockMultipartFile("banner", "banner.jpg", "image/jpeg", "img".getBytes()))
                .header("Authorization", bearer(adminToken)));
        assertNonServerError(putJson("/api/v1/admin/races/999999", adminToken, """
                {
                  "name": "Smoke Race Update",
                  "distance": "1200m",
                  "scheduledStartAt": "2026-06-03T09:00:00",
                  "scheduledEndAt": "2026-06-03T09:30:00",
                  "minParticipants": 2,
                  "maxParticipants": 8,
                  "entryFee": 0,
                  "prizes": []
                }
                """));
        assertNonServerError(delete("/api/v1/admin/races/999999")
                .header("Authorization", bearer(adminToken)));
        assertNonServerError(delete("/api/v1/admin/tournaments/999999")
                .header("Authorization", bearer(adminToken)));
    }

    private void exerciseRaceSchedulingApis() throws Exception {
        assertNonServerError(put("/api/v1/admin/tournaments/999999/schedule")
                .header("Authorization", bearer(adminToken)));
        assertNonServerError(get("/api/v1/admin/races/999999/participants")
                .header("Authorization", bearer(adminToken)));
        assertNonServerError(putJson("/api/v1/admin/races/999999/participants/999999/gate", adminToken, """
                {
                  "gateNumber": 1
                }
                """));
        assertNonServerError(putJson("/api/v1/admin/races/999999/referee", adminToken, """
                {
                  "refereeId": %d
                }
                """.formatted(admin.getId())));
        assertNonServerError(putJson("/api/v1/admin/races/999999/cancel", adminToken, """
                {
                  "note": "Smoke cancel"
                }
                """));
    }

    private void exercisePhase9RaceOperationApis() throws Exception {
        assertNonServerError(get("/api/v1/referee/races/999999/participants")
                .header("Authorization", bearer(jockeyToken)));
        assertNonServerError(putJson("/api/v1/referee/races/999999/participants/999999/check-in", jockeyToken, """
                {
                  "status": "CHECKED_IN",
                  "note": "Smoke check-in"
                }
                """));
        assertNonServerError(put("/api/v1/referee/races/999999/start")
                .header("Authorization", bearer(jockeyToken)));
        assertNonServerError(postJson("/api/v1/races/999999/complaints", ownerToken, """
                {
                  "accusedParticipantId": 999999,
                  "reason": "Smoke complaint",
                  "evidenceUrl": "https://example.com/evidence"
                }
                """));
        assertOk(get("/api/v1/owner/race-complaints").header("Authorization", bearer(ownerToken)));
        assertOk(get("/api/v1/admin/race-complaints").header("Authorization", bearer(adminToken)));
        assertNonServerError(putJson("/api/v1/admin/race-complaints/999999/resolve", adminToken, """
                {
                  "status": "REJECTED",
                  "adminNote": "Smoke resolve"
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
                  "amount": 10000,
                  "provider": "ZALOPAY"
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
                  "amount": 10000,
                  "provider": "ZALOPAY"
                }
                """));
        PaymentOrder zaloPayOrder = latestPaymentOrder();
        assertZaloPayOk(signedZaloPayCallbackPayload(zaloPayOrder, "100000001"));

        assertOk(postJson("/api/v1/wallets/me/deposit-orders", userToken, """
                {
                  "amount": 10000,
                  "provider": "ZALOPAY"
                }
                """));
        PaymentOrder secondZaloPayOrder = latestPaymentOrder();
        assertZaloPayOk(signedZaloPayCallbackPayload(secondZaloPayOrder, "100000002"));

        assertOk(put("/api/v1/admin/users/" + deactivationTarget.getId() + "/deactivate")
                .header("Authorization", bearer(adminToken)));
    }

    private void exerciseNotificationApis() throws Exception {
        assertOk(get("/api/v1/notifications").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/notifications").param("status", "UNREAD")
                .header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/notifications/unread-count").header("Authorization", bearer(userToken)));
        notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipient().getId().equals(user.getId()))
                .findFirst()
                .ifPresent(notification -> {
                    try {
                        assertOk(put("/api/v1/notifications/" + notification.getId() + "/read")
                                .header("Authorization", bearer(userToken)));
                    } catch (Exception ex) {
                        throw new AssertionError(ex);
                    }
                });
        assertOk(put("/api/v1/notifications/read-all").header("Authorization", bearer(userToken)));
        assertOk(get("/api/v1/admin/notifications").header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/notifications").param("type", "DEPOSIT_PAID")
                .header("Authorization", bearer(adminToken)));
        assertOk(get("/api/v1/admin/notifications").param("recipientId", String.valueOf(user.getId()))
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

    private void assertZaloPayOk(String payload) throws Exception {
        mockMvc.perform(post("/api/zalopay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.return_code").value(1));
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

    private Long latestNewsId() {
        return newsArticleRepository.findAll().stream()
                .max(Comparator.comparing(news -> news.getId()))
                .orElseThrow()
                .getId();
    }

    private void approveLatestJockeyProfile() {
        var profile = jockeyProfileRepository.findById(latestJockeyProfileId()).orElseThrow();
        profile.setStatus(JockeyStatus.APPROVED);
        profile.setReviewedBy(admin.getId());
        jockeyProfileRepository.save(profile);
    }

    private void seedJockeyProfile() {
        jockeyProfileRepository.save(JockeyProfile.builder()
                .user(jockey)
                .licenseNumber("SMOKE-LICENSE")
                .experienceYears(3)
                .hirePrice(new BigDecimal("50000"))
                .bio("Smoke profile")
                .status(JockeyStatus.PENDING)
                .createdBy(jockey.getUsername())
                .updatedBy(jockey.getUsername())
                .build());
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

    private String signedZaloPayCallbackPayload(PaymentOrder order, String transactionNo) {
        String data = """
                {"app_id":2554,"app_trans_id":"%s","app_time":1779690000000,"app_user":"smoke","amount":%s,"embed_data":"{}","item":"[]","zp_trans_id":%s}
                """.formatted(order.getPaymentLinkId(), order.getAmount().longValueExact(), transactionNo).trim();
        return """
                {
                  "data": "%s",
                  "mac": "%s",
                  "type": 1
                }
                """.formatted(jsonEscape(data), hmacSha256(ZALOPAY_KEY2, data));
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
            throw new IllegalStateException(ex);
        }
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

                @Override
                public void sendRaceScheduled(Race race, User recipient) {
                }

                @Override
                public void sendRaceReminder(Race race, User recipient) {
                }

                @Override
                public void sendRaceComplaintCreated(RaceComplaint complaint) {
                }
            };
        }

        @Bean
        @Primary
        RestOperations smokePaymentRestOperations() {
            return new RestTemplate() {
                @Override
                public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
                    return ResponseEntity.ok(responseType.cast(zaloPayResponse(url, request)));
                }

                @Override
                public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Map<String, ?> uriVariables) {
                    return postForEntity(url, request, responseType);
                }

                @Override
                public <T> ResponseEntity<T> postForEntity(java.net.URI url, Object request, Class<T> responseType) {
                    return postForEntity(url.toString(), request, responseType);
                }

                private Map<String, Object> zaloPayResponse(String url, Object request) {
                    if (url.endsWith("/create")) {
                        String appTransId = "unknown";
                        if (request instanceof HttpEntity<?> entity
                                && entity.getBody() instanceof MultiValueMap<?, ?> body) {
                            Object values = body.get("app_trans_id");
                            if (values instanceof java.util.List<?> list && !list.isEmpty()) {
                                appTransId = String.valueOf(list.get(0));
                            }
                        }
                        return Map.of(
                                "return_code", 1,
                                "return_message", "success",
                                "order_url", "https://sandbox.zalopay.test/order/" + appTransId
                        );
                    }
                    return Map.of("return_code", 3, "return_message", "processing");
                }
            };
        }

    }
}
