package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.UpdatePasswordRequest;
import com.minhthien.hoser_backend.dto.request.UserProfileRequest;
import com.minhthien.hoser_backend.dto.response.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserResponse getCurrentUser(Long userId);

    UserResponse getPublicProfile(Long userId);

    List<UserResponse> getJockeys();

    UserResponse updateProfile(Long userId, UserProfileRequest request);

    UserResponse updateProfile(Long userId, UserProfileRequest request, MultipartFile avatar);

    void updatePassword(Long userId, UpdatePasswordRequest request);

    void deactivateAccount(Long userId);
}
