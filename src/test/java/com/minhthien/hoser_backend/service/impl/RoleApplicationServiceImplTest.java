package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.OwnerRoleApplicationRequest;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.OwnerProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.JockeyStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleApplicationServiceImplTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private OwnerProfileRepository ownerProfileRepository;

    @Mock
    private SpectatorProfileRepository spectatorProfileRepository;

    @Mock
    private RefereeProfileRepository refereeProfileRepository;

    @Mock
    private JockeyProfileRepository jockeyProfileRepository;

    @Mock
    private CloudinaryUploadService cloudinaryUploadService;

    @Mock
    private MailService mailService;

    @Test
    void submitOwnerApplicationKeepsUserRoleAsUserAndMarksPending() {
        RoleApplicationServiceImpl service = service();
        User user = user(1L, "owner-candidate", UserRole.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ownerProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> {
            OwnerProfile profile = invocation.getArgument(0);
            profile.setId(10L);
            return profile;
        });

        var response = service.submitOwnerApplication(1L, ownerRequest(), null);

        assertThat(response.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(response.getStatus()).isEqualTo(RoleApprovalStatus.PENDING);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getPendingRole()).isEqualTo(UserRole.OWNER);
        assertThat(user.getRoleApprovalStatus()).isEqualTo(RoleApprovalStatus.PENDING);
    }

    @Test
    void pendingUserCannotSubmitAnotherRoleApplication() {
        RoleApplicationServiceImpl service = service();
        User user = user(1L, "pending-user", UserRole.USER);
        user.setPendingRole(UserRole.OWNER);
        user.setRoleApprovalStatus(RoleApprovalStatus.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.submitJockeyApplication(1L, jockeyRequest(), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A role application is already pending");
    }

    @Test
    void submitJockeyApplicationReusesJockeyProfileAndKeepsUserAsUser() {
        RoleApplicationServiceImpl service = service();
        User user = user(1L, "jockey-candidate", UserRole.USER);
        JockeyProfile profile = JockeyProfile.builder()
                .id(20L)
                .user(user)
                .status(JockeyStatus.REJECTED)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jockeyProfileRepository.existsByLicenseNumberAndUserIdNot("LIC-1", 1L)).thenReturn(false);
        when(jockeyProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jockeyProfileRepository.save(any(JockeyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.submitJockeyApplication(1L, jockeyRequest(), null, null);

        assertThat(response.getRole()).isEqualTo(UserRole.JOCKEY);
        assertThat(response.getStatus()).isEqualTo(RoleApprovalStatus.PENDING);
        assertThat(profile.getStatus()).isEqualTo(JockeyStatus.PENDING);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getPendingRole()).isEqualTo(UserRole.JOCKEY);
    }

    @Test
    void adminApproveOwnerApplicationChangesUserRole() {
        RoleApplicationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User candidate = user(1L, "owner-candidate", UserRole.USER);
        candidate.setPendingRole(UserRole.OWNER);
        candidate.setRoleApprovalStatus(RoleApprovalStatus.PENDING);
        OwnerProfile profile = OwnerProfile.builder()
                .id(10L)
                .user(candidate)
                .stableName("Stable")
                .address("Address")
                .status(RoleApprovalStatus.PENDING)
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(ownerProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveApplication(10L, 9L);

        assertThat(response.getStatus()).isEqualTo(RoleApprovalStatus.APPROVED);
        assertThat(candidate.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(candidate.getRoleApprovalStatus()).isEqualTo(RoleApprovalStatus.APPROVED);
        assertThat(profile.getStatus()).isEqualTo(RoleApprovalStatus.APPROVED);
        verify(mailService).sendRoleApplicationApproved(candidate, UserRole.OWNER);
    }

    @Test
    void adminApproveOwnerApplicationIgnoresSameIdNonPendingProfilesFromOtherRoles() {
        RoleApplicationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User ownerCandidate = user(1L, "owner-candidate", UserRole.USER);
        User jockeyUser = user(2L, "jockey-user", UserRole.JOCKEY);
        OwnerProfile ownerProfile = OwnerProfile.builder()
                .id(1L)
                .user(ownerCandidate)
                .stableName("Stable")
                .address("Address")
                .status(RoleApprovalStatus.PENDING)
                .build();
        JockeyProfile jockeyProfile = JockeyProfile.builder()
                .id(1L)
                .user(jockeyUser)
                .status(JockeyStatus.APPROVED)
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(ownerProfileRepository.findById(1L)).thenReturn(Optional.of(ownerProfile));
        when(jockeyProfileRepository.findById(1L)).thenReturn(Optional.of(jockeyProfile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveApplication(1L, 9L);

        assertThat(response.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(response.getStatus()).isEqualTo(RoleApprovalStatus.APPROVED);
        assertThat(ownerCandidate.getRole()).isEqualTo(UserRole.OWNER);
        verify(mailService).sendRoleApplicationApproved(ownerCandidate, UserRole.OWNER);
    }

    @Test
    void adminApproveCanUseRoleWhenMultiplePendingApplicationsShareProfileId() {
        RoleApplicationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User ownerCandidate = user(1L, "owner-candidate", UserRole.USER);
        OwnerProfile ownerProfile = OwnerProfile.builder()
                .id(1L)
                .user(ownerCandidate)
                .stableName("Stable")
                .address("Address")
                .status(RoleApprovalStatus.PENDING)
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(ownerProfileRepository.findById(1L)).thenReturn(Optional.of(ownerProfile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.approveApplication(1L, 9L, UserRole.OWNER);

        assertThat(response.getRole()).isEqualTo(UserRole.OWNER);
        assertThat(response.getStatus()).isEqualTo(RoleApprovalStatus.APPROVED);
        assertThat(ownerCandidate.getRole()).isEqualTo(UserRole.OWNER);
        verify(mailService).sendRoleApplicationApproved(ownerCandidate, UserRole.OWNER);
    }

    @Test
    void adminRejectOwnerApplicationSendsRejectedEmailWithReason() {
        RoleApplicationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User candidate = user(1L, "owner-candidate", UserRole.USER);
        candidate.setPendingRole(UserRole.OWNER);
        candidate.setRoleApprovalStatus(RoleApprovalStatus.PENDING);
        OwnerProfile profile = OwnerProfile.builder()
                .id(10L)
                .user(candidate)
                .stableName("Stable")
                .address("Address")
                .status(RoleApprovalStatus.PENDING)
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(ownerProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.rejectApplication(10L, 9L, reviewRequest("Missing document"));

        assertThat(response.getStatus()).isEqualTo(RoleApprovalStatus.REJECTED);
        assertThat(candidate.getRole()).isEqualTo(UserRole.USER);
        assertThat(candidate.getRoleApprovalStatus()).isEqualTo(RoleApprovalStatus.REJECTED);
        assertThat(profile.getStatus()).isEqualTo(RoleApprovalStatus.REJECTED);
        verify(mailService).sendRoleApplicationRejected(candidate, UserRole.OWNER, "Missing document");
    }

    @Test
    void adminApproveFailsWhenApprovedEmailFails() {
        RoleApplicationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User candidate = user(1L, "owner-candidate", UserRole.USER);
        OwnerProfile profile = OwnerProfile.builder()
                .id(10L)
                .user(candidate)
                .stableName("Stable")
                .address("Address")
                .status(RoleApprovalStatus.PENDING)
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(ownerProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("smtp down"))
                .when(mailService).sendRoleApplicationApproved(eq(candidate), eq(UserRole.OWNER));

        assertThatThrownBy(() -> service.approveApplication(10L, 9L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");
    }

    @Test
    void adminRejectFailsWhenRejectedEmailFails() {
        RoleApplicationServiceImpl service = service();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User candidate = user(1L, "owner-candidate", UserRole.USER);
        OwnerProfile profile = OwnerProfile.builder()
                .id(10L)
                .user(candidate)
                .stableName("Stable")
                .address("Address")
                .status(RoleApprovalStatus.PENDING)
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(ownerProfileRepository.findById(10L)).thenReturn(Optional.of(profile));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ownerProfileRepository.save(any(OwnerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("smtp down"))
                .when(mailService).sendRoleApplicationRejected(eq(candidate), eq(UserRole.OWNER), eq("Missing document"));

        assertThatThrownBy(() -> service.rejectApplication(10L, 9L, reviewRequest("Missing document")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");
    }

    private RoleApplicationServiceImpl service() {
        return new RoleApplicationServiceImpl(
                userRepository,
                ownerProfileRepository,
                spectatorProfileRepository,
                refereeProfileRepository,
                jockeyProfileRepository,
                cloudinaryUploadService,
                mailService
        );
    }

    private AdminReviewRequest reviewRequest(String reason) {
        AdminReviewRequest request = new AdminReviewRequest();
        request.setReason(reason);
        return request;
    }

    private OwnerRoleApplicationRequest ownerRequest() {
        OwnerRoleApplicationRequest request = new OwnerRoleApplicationRequest();
        request.setStableName("Smoke Stable");
        request.setExperienceYears(3);
        request.setAddress("Ho Chi Minh City");
        request.setBio("Owner bio");
        return request;
    }

    private JockeyProfileRequest jockeyRequest() {
        JockeyProfileRequest request = new JockeyProfileRequest();
        request.setLicenseNumber("LIC-1");
        request.setHirePrice(new BigDecimal("500.00"));
        request.setBio("Jockey bio");
        return request;
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .active(true)
                .build();
    }
}
