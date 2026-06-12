package com.minhthien.hoser_backend.service.kyc;

import com.minhthien.hoser_backend.dto.kyc.FptFaceMatchResult;
import com.minhthien.hoser_backend.entity.KycVerification;
import com.minhthien.hoser_backend.entity.SpectatorProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.KycVerificationRepository;
import com.minhthien.hoser_backend.repository.OwnerProfileRepository;
import com.minhthien.hoser_backend.repository.RefereeProfileRepository;
import com.minhthien.hoser_backend.repository.SpectatorProfileRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycCompletionServiceTest {
    @Mock private KycVerificationRepository kycVerificationRepository;
    @Mock private OwnerProfileRepository ownerProfileRepository;
    @Mock private JockeyProfileRepository jockeyProfileRepository;
    @Mock private RefereeProfileRepository refereeProfileRepository;
    @Mock private SpectatorProfileRepository spectatorProfileRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private KycCompletionService service;

    @Test
    void spectatorCompletionLinksKycAndMovesApplicationToPending() {
        User user = User.builder()
                .id(1L)
                .username("spectator")
                .email("spectator@example.com")
                .role(UserRole.USER)
                .roleApprovalStatus(RoleApprovalStatus.NONE)
                .active(true)
                .build();
        KycVerification verification = KycVerification.builder()
                .id(20L)
                .user(user)
                .requestedRole(UserRole.SPECTATOR)
                .status(KycStatus.OCR_PASSED)
                .build();
        SpectatorProfile profile = SpectatorProfile.builder()
                .id(30L)
                .user(user)
                .displayName("Fan")
                .status(RoleApprovalStatus.DRAFT)
                .build();
        when(kycVerificationRepository.findByIdAndUserId(20L, 1L))
                .thenReturn(Optional.of(verification));
        when(spectatorProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(spectatorProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Long profileId = service.complete(20L, 1L, "selfie-url",
                new FptFaceMatchResult(true, new BigDecimal("92.5"), "{}", null));

        assertEquals(30L, profileId);
        assertEquals(KycStatus.PASSED, verification.getStatus());
        assertEquals("selfie-url", verification.getSelfieImageUrl());
        assertEquals(RoleApprovalStatus.PENDING, profile.getStatus());
        assertSame(verification, profile.getKycVerification());
        assertEquals(UserRole.SPECTATOR, user.getPendingRole());
        assertEquals(RoleApprovalStatus.PENDING, user.getRoleApprovalStatus());
        verify(userRepository).save(user);
        verify(kycVerificationRepository).save(verification);
    }
}
