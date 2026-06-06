package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.LoginRequest;
import com.minhthien.hoser_backend.dto.request.TwoFactorVerifyRequest;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.TwoFactorChallenge;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.PasswordResetOtpRepository;
import com.minhthien.hoser_backend.repository.TwoFactorChallengeRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.security.JwtTokenProvider;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.SystemSettingsService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private PasswordResetOtpRepository otpRepository;
    @Mock private MailService mailService;
    @Mock private WalletService walletService;
    @Mock private TwoFactorChallengeRepository challengeRepository;
    @Mock private SystemSettingsService systemSettingsService;

    @InjectMocks
    private AuthServiceImpl service;

    @Test
    void adminLoginReturnsChallengeWithoutJwtWhenTwoFactorIsRequired() {
        User admin = user();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(admin);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(systemSettingsService.requiresTwoFactor(UserRole.ADMIN)).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(challengeRepository.save(any(TwoFactorChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("secret");

        var response = service.login(request);

        assertTrue(response.getTwoFactorRequired());
        assertNull(response.getToken());
        assertNotNull(response.getChallengeId());
        verify(mailService).sendTwoFactorOtp(eq(admin), matches("\\d{6}"));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void successfulVerificationConsumesChallengeAndUsesConfiguredSessionDuration() {
        User admin = user();
        TwoFactorChallenge challenge = TwoFactorChallenge.builder()
                .id("challenge")
                .user(admin)
                .otpHash("hash")
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .attemptCount(0)
                .build();
        SystemSettings settings = SystemSettings.builder().sessionDurationMinutes(60).build();
        when(challengeRepository.findDetailedById("challenge")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);
        when(systemSettingsService.getCurrent()).thenReturn(settings);
        when(jwtTokenProvider.generateTokenFromUsername("admin", 3_600_000L)).thenReturn("jwt");
        TwoFactorVerifyRequest request = new TwoFactorVerifyRequest();
        request.setChallengeId("challenge");
        request.setOtp("123456");

        var response = service.verifyTwoFactor(request);

        assertEquals("jwt", response.getToken());
        assertFalse(response.getTwoFactorRequired());
        assertNotNull(challenge.getUsedAt());
        verify(challengeRepository).save(challenge);
    }

    private User user() {
        return User.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .role(UserRole.ADMIN)
                .active(true)
                .build();
    }
}
