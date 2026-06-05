package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.UpdatePasswordRequest;
import com.minhthien.hoser_backend.dto.request.UserProfileRequest;
import com.minhthien.hoser_backend.dto.response.UserResponse;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private static final String USER_AVATAR_FOLDER = "hoser/users/avatars";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryUploadService cloudinaryUploadService;

    @Override
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getJockeys() {
        return userRepository.findByRoleAndActiveOrderByCreatedAtDesc(UserRole.JOCKEY, true).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UserProfileRequest request) {
        return updateProfile(userId, request, null);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UserProfileRequest request, MultipartFile avatar) {
        if (request == null) {
            throw new BadRequestException("User profile request is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (avatar != null) {
            user.setAvatarUrl(cloudinaryUploadService.uploadImage(avatar, USER_AVATAR_FOLDER));
        }
        if (request.getLocation() != null) {
            user.setLocation(request.getLocation());
        }
        user.setUpdatedBy(user.getUsername());
        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, UpdatePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BadRequestException("Password login is not enabled for this account");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deactivateAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setActive(false);
        userRepository.save(user);
    }

    private UserResponse mapToResponse(User user) {
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
                .location(user.getLocation())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
