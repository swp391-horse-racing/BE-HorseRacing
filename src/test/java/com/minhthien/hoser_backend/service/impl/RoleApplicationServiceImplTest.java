package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.SpectatorRoleApplicationRequest;
import com.minhthien.hoser_backend.entity.KycVerification;
import com.minhthien.hoser_backend.entity.SpectatorProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.KycStatus;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.OwnerProfileRepository;
import com.minhthien.hoser_backend.repository.RefereeProfileRepository;
import com.minhthien.hoser_backend.repository.SpectatorProfileRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private OwnerProfileRepository ownerProfileRepository;
    @Mock private SpectatorProfileRepository spectatorProfileRepository;
    @Mock private RefereeProfileRepository refereeProfileRepository;
    @Mock private JockeyProfileRepository jockeyProfileRepository;
    @Mock private CloudinaryUploadService cloudinaryUploadService;
    @Mock private MailService mailService;

    @InjectMocks
    private RoleApplicationServiceImpl service;

    @Test
    void submitSpectatorApplicationCreatesDraftWithoutChangingUserRole() {
        User user = user();
        SpectatorRoleApplicationRequest request = new SpectatorRoleApplicationRequest();
        request.setDisplayName("Race Fan");
        request.setPhone("0900000000");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(jockeyProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(refereeProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(spectatorProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(spectatorProfileRepository.save(any())).thenAnswer(invocation -> {
            SpectatorProfile profile = invocation.getArgument(0);
            profile.setId(30L);
            return profile;
        });

        var response = service.submitSpectatorApplication(1L, request);

        assertEquals(RoleApprovalStatus.DRAFT, response.getStatus());
        assertEquals(UserRole.USER, user.getRole());
        assertEquals(RoleApprovalStatus.NONE, user.getRoleApprovalStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void approveSpectatorFailsWhenKycHasNotPassed() {
        User admin = admin();
        SpectatorProfile profile = SpectatorProfile.builder()
                .id(30L)
                .user(user())
                .displayName("Race Fan")
                .status(RoleApprovalStatus.PENDING)
                .build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(spectatorProfileRepository.findById(30L)).thenReturn(Optional.of(profile));

        assertThrows(BadRequestException.class,
                () -> service.approveApplication(30L, 99L, UserRole.SPECTATOR));

        verify(userRepository, never()).save(profile.getUser());
        verify(spectatorProfileRepository, never()).save(any());
    }

    @Test
    void approveSpectatorPassesWhenKycPassed() {
        User admin = admin();
        User user = user();
        KycVerification verification = KycVerification.builder()
                .id(20L)
                .user(user)
                .requestedRole(UserRole.SPECTATOR)
                .status(KycStatus.PASSED)
                .build();
        SpectatorProfile profile = SpectatorProfile.builder()
                .id(30L)
                .user(user)
                .displayName("Race Fan")
                .kycVerification(verification)
                .status(RoleApprovalStatus.PENDING)
                .build();
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));
        when(spectatorProfileRepository.findById(30L)).thenReturn(Optional.of(profile));
        when(spectatorProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveApplication(30L, 99L, UserRole.SPECTATOR);

        assertEquals(RoleApprovalStatus.APPROVED, response.getStatus());
        assertEquals(UserRole.SPECTATOR, user.getRole());
        assertEquals(RoleApprovalStatus.APPROVED, user.getRoleApprovalStatus());
        verify(userRepository).save(user);
        verify(mailService).sendRoleApplicationApproved(user, UserRole.SPECTATOR);
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

    private User admin() {
        return User.builder()
                .id(99L)
                .username("admin")
                .email("admin@example.com")
                .role(UserRole.ADMIN)
                .roleApprovalStatus(RoleApprovalStatus.APPROVED)
                .active(true)
                .build();
    }
}
