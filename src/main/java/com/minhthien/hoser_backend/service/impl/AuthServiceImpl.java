package com.minhthien.hoser_backend.service.impl;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.minhthien.hoser_backend.dto.request.LoginRequest;
import com.minhthien.hoser_backend.dto.request.RegisterRequest;
import com.minhthien.hoser_backend.dto.request.TwoFactorResendRequest;
import com.minhthien.hoser_backend.dto.request.TwoFactorVerifyRequest;
import com.minhthien.hoser_backend.dto.response.AuthResponse;
import com.minhthien.hoser_backend.dto.response.UserResponse;
import com.minhthien.hoser_backend.entity.PasswordResetOtp;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.TwoFactorChallenge;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.PasswordResetOtpRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.repository.TwoFactorChallengeRepository;
import com.minhthien.hoser_backend.security.JwtTokenProvider;
import com.minhthien.hoser_backend.service.AuthService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.WalletService;
import com.minhthien.hoser_backend.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final PasswordResetOtpRepository otpRepository;
    private final MailService mailService;
    private final WalletService walletService;
    private final TwoFactorChallengeRepository twoFactorChallengeRepository;
    private final SystemSettingsService systemSettingsService;

    private static final int TWO_FACTOR_EXPIRY_MINUTES = 5;
    private static final int TWO_FACTOR_MAX_ATTEMPTS = 5;

    @Value("${google.client-id}")
    private String googleClientId;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already in use: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already taken: " + request.getUsername());
        }
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .Phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.USER)
                .active(true)
                .build();

        user = userRepository.save(user);
        walletService.getOrCreateUserWallet(user.getId());
        return completeLogin(user);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = (User) authentication.getPrincipal();
        ensureDefaultUserRole(user);
        return completeLogin(user);
    }

    @Override
    public UserResponse getCurrentUser(String usernameOrEmail) {
        User user = userRepository.findByUsername(usernameOrEmail)
                .orElseGet(() -> userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + usernameOrEmail)));

        return mapToUserResponse(user);
    }

    @Override
    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found"));

        String otp = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
        PasswordResetOtp resetOtp = PasswordResetOtp.builder()
                .email(email)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .build();

        otpRepository.save(resetOtp);
        mailService.sendOtp(email, otp);
    }

    @Override
    public void resetPassword(String email, String otp, String newPassword) {
        PasswordResetOtp resetOtp = otpRepository.findByEmailAndOtp(email, otp)
                .orElseThrow(() -> new BadRequestException("Invalid OTP"));

        if (resetOtp.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public AuthResponse loginGoogle(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new BadRequestException("Google token is required");
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleToken = verifier.verify(idToken);
            if (googleToken == null) {
                throw new BadRequestException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = googleToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String picture = (String) payload.get("picture");

            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .email(email)
                            .username(name)
                            .avatarUrl(picture)
                            .role(UserRole.USER)
                            .active(true)
                            .build()));
            ensureDefaultUserRole(user);

            return completeLogin(user);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid Google token");
        }
    }

    @Override
    public AuthResponse loginFacebook(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BadRequestException("Facebook access token is required");
        }
        try {
            String url = "https://graph.facebook.com/me?fields=id,name,email,picture&access_token=" + accessToken;
            RestTemplate restTemplate = new RestTemplate();
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                throw new BadRequestException("Invalid Facebook token");
            }

            String email = (String) response.get("email");
            String name = (String) response.get("name");
            String id = (String) response.get("id");

            if (email == null) {
                email = id + "@facebook.com";
            }

            String finalEmail = email;
            User user = userRepository.findByEmail(finalEmail)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .email(finalEmail)
                            .username(name)
                            .role(UserRole.USER)
                            .active(true)
                            .provider("FACEBOOK")
                            .build()));
            ensureDefaultUserRole(user);

            return completeLogin(user);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid Facebook token");
        }
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .pendingRole(user.getPendingRole())
                .roleApprovalStatus(user.getRoleApprovalStatus())
                .roleReviewReason(user.getRoleReviewReason())
                .twoFactorRequired(false)
                .build();
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse verifyTwoFactor(TwoFactorVerifyRequest request) {
        TwoFactorChallenge challenge = twoFactorChallengeRepository.findDetailedById(request.getChallengeId())
                .orElseThrow(() -> new BadRequestException("Invalid two-factor challenge"));
        if (challenge.getUsedAt() != null) {
            throw new BadRequestException("Two-factor challenge has already been used");
        }
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Two-factor challenge has expired");
        }
        if (challenge.getAttemptCount() >= TWO_FACTOR_MAX_ATTEMPTS) {
            throw new BadRequestException("Two-factor challenge has too many failed attempts");
        }
        if (!passwordEncoder.matches(request.getOtp(), challenge.getOtpHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            twoFactorChallengeRepository.save(challenge);
            throw new BadRequestException("Invalid two-factor code");
        }
        challenge.setUsedAt(LocalDateTime.now());
        twoFactorChallengeRepository.save(challenge);
        return authenticatedResponse(challenge.getUser());
    }

    @Override
    @Transactional
    public AuthResponse resendTwoFactor(TwoFactorResendRequest request) {
        TwoFactorChallenge challenge = twoFactorChallengeRepository.findDetailedById(request.getChallengeId())
                .orElseThrow(() -> new BadRequestException("Invalid two-factor challenge"));
        if (challenge.getUsedAt() != null) {
            throw new BadRequestException("Two-factor challenge has already been used");
        }
        String otp = generateOtp();
        challenge.setOtpHash(passwordEncoder.encode(otp));
        challenge.setExpiresAt(LocalDateTime.now().plusMinutes(TWO_FACTOR_EXPIRY_MINUTES));
        challenge.setAttemptCount(0);
        twoFactorChallengeRepository.save(challenge);
        mailService.sendTwoFactorOtp(challenge.getUser(), otp);
        return challengeResponse(challenge);
    }

    private AuthResponse completeLogin(User user) {
        if (systemSettingsService.requiresTwoFactor(user.getRole())) {
            return createTwoFactorChallenge(user);
        }
        return authenticatedResponse(user);
    }

    private AuthResponse createTwoFactorChallenge(User user) {
        String otp = generateOtp();
        TwoFactorChallenge challenge = twoFactorChallengeRepository.save(TwoFactorChallenge.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(LocalDateTime.now().plusMinutes(TWO_FACTOR_EXPIRY_MINUTES))
                .build());
        mailService.sendTwoFactorOtp(user, otp);
        return challengeResponse(challenge);
    }

    private AuthResponse challengeResponse(TwoFactorChallenge challenge) {
        User user = challenge.getUser();
        return AuthResponse.builder()
                .token(null)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole())
                .pendingRole(user.getPendingRole())
                .roleApprovalStatus(user.getRoleApprovalStatus())
                .roleReviewReason(user.getRoleReviewReason())
                .twoFactorRequired(true)
                .challengeId(challenge.getId())
                .challengeExpiresAt(challenge.getExpiresAt())
                .build();
    }

    private AuthResponse authenticatedResponse(User user) {
        long expirationMs = systemSettingsService.getCurrent().getSessionDurationMinutes() * 60_000L;
        String token = jwtTokenProvider.generateTokenFromUsername(user.getUsername(), expirationMs);
        return buildAuthResponse(user, token);
    }

    private String generateOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private void ensureDefaultUserRole(User user) {
        boolean changed = false;
        if (user.getRole() == null) {
            user.setRole(UserRole.USER);
            changed = true;
        }
        if (user.getRoleApprovalStatus() == null) {
            user.setRoleApprovalStatus(RoleApprovalStatus.NONE);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .pendingRole(user.getPendingRole())
                .roleApprovalStatus(user.getRoleApprovalStatus())
                .roleReviewReason(user.getRoleReviewReason())
                .roleReviewedBy(user.getRoleReviewedBy())
                .roleReviewedAt(user.getRoleReviewedAt())
                .active(user.getActive())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

