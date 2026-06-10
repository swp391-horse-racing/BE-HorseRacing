package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.kyc.FptFaceMatchResult;
import com.minhthien.hoser_backend.dto.kyc.FptOcrResult;
import com.minhthien.hoser_backend.entity.KycVerification;
import com.minhthien.hoser_backend.entity.OwnerProfile;
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

    private MockMultipartFile image(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("user")
                .email("user@example.com")
                .role(UserRole.USER)
                .roleApprovalStatus(RoleApprovalStatus.NONE)
                .active(true)
                .build();
    }
}
