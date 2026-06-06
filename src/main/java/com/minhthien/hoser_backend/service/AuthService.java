package com.minhthien.hoser_backend.service;


import com.minhthien.hoser_backend.dto.request.LoginRequest;
import com.minhthien.hoser_backend.dto.request.RegisterRequest;
import com.minhthien.hoser_backend.dto.request.TwoFactorResendRequest;
import com.minhthien.hoser_backend.dto.request.TwoFactorVerifyRequest;
import com.minhthien.hoser_backend.dto.response.AuthResponse;
import com.minhthien.hoser_backend.dto.response.UserResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    UserResponse getCurrentUser(String email);

    void forgotPassword(String email);

    void resetPassword(String email, String otp, String newPassword);

    AuthResponse loginGoogle(String idToken);
    AuthResponse loginFacebook(String accessToken);

    AuthResponse verifyTwoFactor(TwoFactorVerifyRequest request);

    AuthResponse resendTwoFactor(TwoFactorResendRequest request);

}
