package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.kyc.FptFaceMatchResult;
import com.minhthien.hoser_backend.dto.kyc.FptOcrResult;
import com.minhthien.hoser_backend.entity.KycVerification;
import com.minhthien.hoser_backend.entity.OwnerProfile;
import com.minhthien.hoser_backend.entity.SpectatorProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.*;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.kyc.FptAiClient;
import com.minhthien.hoser_backend.service.kyc.KycCompletionService;
import com.minhthien.hoser_backend.service.kyc.KycFailurePersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private OwnerProfileRepository ownerProfileRepository;
    @Mock private JockeyProfileRepository jockeyProfileRepository;
    @Mock private RefereeProfileRepository refereeProfileRepository;
    @Mock private SpectatorProfileRepository spectatorProfileRepository;
    @Mock private KycVerificationRepository kycVerificationRepository;
    @Mock private CloudinaryUploadService cloudinaryUploadService;
    @Mock private FptAiClient fptAiClient;
    @Mock private KycFailurePersistenceService failurePersistenceService;
    @Mock private KycCompletionService completionService;

    @InjectMocks
    private KycServiceImpl service;

    @Test
    void successfulOcrReturnsMaskedIdAndKeepsProfileDraft() {
        User user = user();
        OwnerProfile draft = OwnerProfile.builder()
                .id(10L).user(user).stableName("Stable").address("Address")
                .status(RoleApprovalStatus.DRAFT).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(draft));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                true, "012345678901", "NGUYEN VAN A", "01/01/2000",
                "Nam", "HCM", "01/01/2020", "{}", null));
        when(kycVerificationRepository.save(any())).thenAnswer(invocation -> {
            KycVerification value = invocation.getArgument(0);
            value.setId(20L);
            return value;
        });

        var response = service.verifyCccd(
                1L, UserRole.OWNER, image("front"), image("back"));

        assertEquals(KycStatus.OCR_PASSED, response.getKycStatus());
        assertEquals("********8901", response.getIdNumberMasked());
        assertEquals(RoleApprovalStatus.DRAFT, draft.getStatus());
        verifyNoInteractions(completionService);
    }

    @Test
    void successfulOcrAllowsNormalizedFullNameMatch() {
        User user = user();
        user.setFullName("Ngô Đình Minh Thiện");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                OwnerProfile.builder().user(user).stableName("Stable").address("Address")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                true, "012345678901", "NGO DINH MINH THIEN", "01/01/2000",
                "Nam", "HCM", "01/01/2020", "{}", null));
        when(kycVerificationRepository.save(any())).thenAnswer(invocation -> {
            KycVerification value = invocation.getArgument(0);
            value.setId(20L);
            return value;
        });

        var response = service.verifyCccd(
                1L, UserRole.OWNER, image("front"), image("back"));

        assertEquals(KycStatus.OCR_PASSED, response.getKycStatus());
        assertEquals("NGO DINH MINH THIEN", response.getFullName());
    }

    @Test
    void rejectsOcrWhenCccdFullNameDoesNotMatchAccountFullName() {
        User user = user();
        user.setFullName("NGO DINH MINH THIEN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                OwnerProfile.builder().user(user).stableName("Stable").address("Address")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                true, "012345678901", "CHÂU THÀNH", "01/01/2000",
                "Nam", "HCM", "01/01/2020", "{}", null));
        when(failurePersistenceService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyCccd(1L, UserRole.OWNER, image("front"), image("back")));

        assertTrue(error.getMessage().contains("Họ và tên trên CCCD không khớp với họ tên tài khoản"));
        verify(failurePersistenceService).save(argThat(v ->
                v.getStatus() == KycStatus.FAILED
                        && "Họ và tên trên CCCD không khớp với họ tên tài khoản".equals(v.getRejectReason())));
        verify(kycVerificationRepository, never()).save(any());
    }

    @Test
    void rejectsOcrWhenAccountFullNameIsBlank() {
        User user = user();
        user.setFullName(" ");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                OwnerProfile.builder().user(user).stableName("Stable").address("Address")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                true, "012345678901", "NGO DINH MINH THIEN", "01/01/2000",
                "Nam", "HCM", "01/01/2020", "{}", null));
        when(failurePersistenceService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyCccd(1L, UserRole.OWNER, image("front"), image("back")));

        assertTrue(error.getMessage().contains("Vui lòng cập nhật họ và tên tài khoản trước khi KYC"));
        verify(failurePersistenceService).save(argThat(v ->
                v.getStatus() == KycStatus.FAILED
                        && "Vui lòng cập nhật họ và tên tài khoản trước khi KYC".equals(v.getRejectReason())));
        verify(kycVerificationRepository, never()).save(any());
    }

    @Test
    void ocrFailureIsPersistedAndStopsTheWizard() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                OwnerProfile.builder().user(user).stableName("Stable").address("Address")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                false, null, null, null, null, null, null, "{}", "Không đọc được CCCD mặt trước"));
        when(failurePersistenceService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyCccd(1L, UserRole.OWNER, image("front"), image("back")));

        assertTrue(error.getMessage().contains("Không đọc được CCCD"));
        verify(failurePersistenceService).save(argThat(v -> v.getStatus() == KycStatus.FAILED));
    }

    @Test
    void faceMismatchPersistsFailureAndDoesNotSubmitDraft() {
        User user = user();
        KycVerification verification = KycVerification.builder()
                .id(20L).user(user).requestedRole(UserRole.OWNER)
                .status(KycStatus.OCR_PASSED).frontImageUrl("front-url").build();
        when(kycVerificationRepository.findByIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(verification));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                OwnerProfile.builder().user(user).stableName("Stable").address("Address")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString())).thenReturn("selfie-url");
        when(fptAiClient.download("front-url")).thenReturn(new byte[]{1});
        when(fptAiClient.callFaceMatch(any(), anyString(), any())).thenReturn(
                new FptFaceMatchResult(false, new BigDecimal("65"), "{}", "Điểm khớp khuôn mặt quá thấp"));
        when(failurePersistenceService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(BadRequestException.class,
                () -> service.verifyFace(1L, 20L, image("selfie")));

        verify(failurePersistenceService).save(argThat(v -> v.getStatus() == KycStatus.FAILED));
        verifyNoInteractions(completionService);
    }

    @Test
    void rejectsOversizedImageBeforeExternalCalls() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "cccdFront", "front.jpg", "image/jpeg", oversized);
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                OwnerProfile.builder().user(user).stableName("Stable").address("Address")
                        .status(RoleApprovalStatus.DRAFT).build()));

        assertThrows(BadRequestException.class,
                () -> service.verifyCccd(1L, UserRole.OWNER, file, image("back")));

        verifyNoInteractions(fptAiClient, cloudinaryUploadService);
    }

    @Test
    void spectatorOcrRejectsUnderageAndPersistsFailure() {
        User user = user();
        String underageDob = LocalDate.now().minusYears(18).plusDays(1)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(spectatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                SpectatorProfile.builder().user(user).displayName("Fan")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                true, "012345678901", "NGUYEN VAN A", underageDob,
                "Nam", "HCM", "01/01/2020", "{}", null));
        when(failurePersistenceService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.verifyCccd(1L, UserRole.SPECTATOR, image("front"), image("back")));

        assertTrue(error.getMessage().contains("18"));
        verify(failurePersistenceService).save(argThat(v -> v.getStatus() == KycStatus.FAILED));
        verify(kycVerificationRepository, never()).save(any());
    }

    @Test
    void spectatorOcrAcceptsAdult() {
        User user = user();
        String adultDob = LocalDate.now().minusYears(18)
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(spectatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                SpectatorProfile.builder().user(user).displayName("Fan")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString()))
                .thenReturn("front-url", "back-url");
        when(fptAiClient.callOcr(any())).thenReturn(new FptOcrResult(
                true, "012345678901", "NGUYEN VAN A", adultDob,
                "Nam", "HCM", "01/01/2020", "{}", null));
        when(kycVerificationRepository.save(any())).thenAnswer(invocation -> {
            KycVerification value = invocation.getArgument(0);
            value.setId(20L);
            return value;
        });

        var response = service.verifyCccd(
                1L, UserRole.SPECTATOR, image("front"), image("back"));

        assertEquals(KycStatus.OCR_PASSED, response.getKycStatus());
        assertEquals(adultDob, response.getDateOfBirth());
    }

    @Test
    void spectatorFaceMatchSubmitsDraftForAdminReview() {
        User user = user();
        KycVerification verification = KycVerification.builder()
                .id(20L).user(user).requestedRole(UserRole.SPECTATOR)
                .status(KycStatus.OCR_PASSED).frontImageUrl("front-url").build();
        when(kycVerificationRepository.findByIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(verification));
        when(spectatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(
                SpectatorProfile.builder().id(30L).user(user).displayName("Fan")
                        .status(RoleApprovalStatus.DRAFT).build()));
        when(cloudinaryUploadService.uploadImage(any(), anyString())).thenReturn("selfie-url");
        when(fptAiClient.download("front-url")).thenReturn(new byte[]{1});
        when(fptAiClient.callFaceMatch(any(), anyString(), any())).thenReturn(
                new FptFaceMatchResult(true, new BigDecimal("90"), "{}", null));
        when(completionService.complete(eq(20L), eq(1L), eq("selfie-url"), any()))
                .thenReturn(30L);

        var response = service.verifyFace(1L, 20L, image("selfie"));

        assertEquals(KycStatus.PASSED, response.getKycStatus());
        assertEquals(RoleApprovalStatus.PENDING, response.getApplicationStatus());
        assertEquals(30L, response.getProfileId());
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("user")
                .email("user@example.com")
                .fullName("NGUYEN VAN A")
                .role(UserRole.USER)
                .roleApprovalStatus(RoleApprovalStatus.NONE)
                .active(true)
                .build();
    }
}
