package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.OwnerRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.RefereeRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.request.SpectatorRoleApplicationRequest;
import com.minhthien.hoser_backend.dto.response.RoleApplicationResponse;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.OwnerProfile;
import com.minhthien.hoser_backend.entity.RefereeProfile;
import com.minhthien.hoser_backend.entity.SpectatorProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.RoleApprovalStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.OwnerProfileRepository;
import com.minhthien.hoser_backend.repository.RefereeProfileRepository;
import com.minhthien.hoser_backend.repository.SpectatorProfileRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.MailService;
import com.minhthien.hoser_backend.service.RoleApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleApplicationServiceImpl implements RoleApplicationService {
    private static final String OWNER_DOCUMENT_FOLDER = "hoser/owners/documents";
    private static final String JOCKEY_AVATAR_FOLDER = "hoser/jockeys/avatars";
    private static final String JOCKEY_ACHIEVEMENTS_FOLDER = "hoser/jockeys/achievements";
    private static final String JOCKEY_LICENSE_DOCUMENT_FOLDER = "hoser/jockeys/license-documents";
    private static final String REFEREE_DOCUMENT_FOLDER = "hoser/referees/documents";

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final SpectatorProfileRepository spectatorProfileRepository;
    private final RefereeProfileRepository refereeProfileRepository;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final MailService mailService;

    @Override
    @Transactional
    public RoleApplicationResponse submitOwnerApplication(Long userId, OwnerRoleApplicationRequest request,
                                                          MultipartFile verificationDocument) {
        User user = requireUser(userId);
        requireCanSubmit(user, UserRole.OWNER);
        OwnerProfile profile = ownerProfileRepository.findByUserId(userId)
                .orElseGet(() -> OwnerProfile.builder()
                        .user(user)
                        .createdBy(user.getUsername())
                        .build());
        profile.setStableName(request.getStableName());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setAddress(request.getAddress());
        profile.setBio(request.getBio());
        if (verificationDocument != null) {
            profile.setVerificationDocumentUrl(cloudinaryUploadService.uploadDocument(
                    verificationDocument, OWNER_DOCUMENT_FOLDER));
        }
        markProfilePending(profile, user);
        prepareUserPending(user, UserRole.OWNER);
        userRepository.save(user);
        return mapOwner(ownerProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public RoleApplicationResponse submitJockeyApplication(Long userId, JockeyProfileRequest request,
                                                           MultipartFile avatar, MultipartFile licenseDocument) {
        User user = requireUser(userId);
        requireCanSubmit(user, UserRole.JOCKEY);
        requireUniqueJockeyLicense(request.getLicenseNumber(), userId);

        JockeyProfile profile = jockeyProfileRepository.findByUserId(userId)
                .orElseGet(() -> JockeyProfile.builder()
                        .user(user)
                        .createdBy(user.getUsername())
                        .build());
        profile.setLicenseNumber(request.getLicenseNumber());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setHeightCm(request.getHeightCm());
        profile.setWeightKg(request.getWeightKg());
        profile.setBio(request.getBio());
        profile.setAwards(request.getAwards());
        profile.setSpecialties(request.getSpecialties());
        if (avatar != null) {
            profile.setAvatarUrl(cloudinaryUploadService.uploadImage(avatar, JOCKEY_AVATAR_FOLDER));
        }
        if (request.getAchievements() != null) {
            profile.setAchievements(cloudinaryUploadService.uploadImage(
                    request.getAchievements(), JOCKEY_ACHIEVEMENTS_FOLDER));
        }
        if (licenseDocument != null) {
            profile.setLicenseDocumentUrl(cloudinaryUploadService.uploadDocument(
                    licenseDocument, JOCKEY_LICENSE_DOCUMENT_FOLDER));
        }
        markJockeyPending(profile, user);
        prepareUserPending(user, UserRole.JOCKEY);
        userRepository.save(user);
        return mapJockey(jockeyProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public RoleApplicationResponse submitSpectatorApplication(Long userId, SpectatorRoleApplicationRequest request) {
        User user = requireUser(userId);
        requireCanSubmit(user, UserRole.SPECTATOR);
        SpectatorProfile profile = spectatorProfileRepository.findByUserId(userId)
                .orElseGet(() -> SpectatorProfile.builder()
                        .user(user)
                        .createdBy(user.getUsername())
                        .build());
        profile.setDisplayName(request.getDisplayName());
        profile.setPhone(request.getPhone());
        profile.setLocation(request.getLocation());
        profile.setFavoriteHorseBreed(request.getFavoriteHorseBreed());
        profile.setBio(request.getBio());
        approveProfileByUser(profile, user);
        approveUserBySelf(user, UserRole.SPECTATOR);
        userRepository.save(user);
        return mapSpectator(spectatorProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public RoleApplicationResponse submitRefereeApplication(Long userId, RefereeRoleApplicationRequest request,
                                                            MultipartFile certificationDocument) {
        User user = requireUser(userId);
        requireCanSubmit(user, UserRole.REFEREE);
        requireUniqueRefereeLicense(request.getLicenseNumber(), userId);
        RefereeProfile profile = refereeProfileRepository.findByUserId(userId)
                .orElseGet(() -> RefereeProfile.builder()
                        .user(user)
                        .createdBy(user.getUsername())
                        .build());
        profile.setLicenseNumber(request.getLicenseNumber());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setSpecialty(request.getSpecialty());
        profile.setBio(request.getBio());
        if (certificationDocument != null) {
            profile.setCertificationDocumentUrl(cloudinaryUploadService.uploadDocument(
                    certificationDocument, REFEREE_DOCUMENT_FOLDER));
        }
        markProfilePending(profile, user);
        prepareUserPending(user, UserRole.REFEREE);
        userRepository.save(user);
        return mapReferee(refereeProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleApplicationResponse getMyApplication(Long userId) {
        User user = requireUser(userId);
        UserRole role = user.getPendingRole() != null ? user.getPendingRole() : user.getRole();
        if (role == null || role == UserRole.USER || role == UserRole.ADMIN) {
            return baseUserApplication(user, null, user.getRoleApprovalStatus());
        }
        return findByUserAndRole(userId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleApplicationResponse> getAdminApplications(UserRole role, RoleApprovalStatus status) {
        if (role != null) {
            requireApplicationRole(role);
            return applicationsByRole(role, status);
        }
        List<RoleApplicationResponse> responses = new ArrayList<>();
        responses.addAll(applicationsByRole(UserRole.OWNER, status));
        responses.addAll(applicationsByRole(UserRole.JOCKEY, status));
        responses.addAll(applicationsByRole(UserRole.SPECTATOR, status));
        responses.addAll(applicationsByRole(UserRole.REFEREE, status));
        return responses.stream()
                .sorted(Comparator.comparing(RoleApplicationResponse::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    @Transactional
    public RoleApplicationResponse approveApplication(Long profileId, Long adminId) {
        return approveApplication(profileId, adminId, null);
    }

    @Override
    @Transactional
    public RoleApplicationResponse approveApplication(Long profileId, Long adminId, UserRole role) {
        requireAdmin(adminId);
        UserRole resolvedRole = resolvePendingApplicationRole(profileId, role);
        return switch (resolvedRole) {
            case OWNER -> approveOwner(profileId, adminId);
            case JOCKEY -> approveJockey(profileId, adminId);
            case SPECTATOR -> approveSpectator(profileId, adminId);
            case REFEREE -> approveReferee(profileId, adminId);
            default -> throw new BadRequestException("Unsupported role application");
        };
    }

    @Override
    @Transactional
    public RoleApplicationResponse rejectApplication(Long profileId, Long adminId, AdminReviewRequest request) {
        return rejectApplication(profileId, adminId, null, request);
    }

    @Override
    @Transactional
    public RoleApplicationResponse rejectApplication(Long profileId, Long adminId, UserRole role, AdminReviewRequest request) {
        requireAdmin(adminId);
        UserRole resolvedRole = resolvePendingApplicationRole(profileId, role);
        String reason = requireReason(request);
        return switch (resolvedRole) {
            case OWNER -> rejectOwner(profileId, adminId, reason);
            case JOCKEY -> rejectJockey(profileId, adminId, reason);
            case SPECTATOR -> rejectSpectator(profileId, adminId, reason);
            case REFEREE -> rejectReferee(profileId, adminId, reason);
            default -> throw new BadRequestException("Unsupported role application");
        };
    }

    private RoleApplicationResponse approveOwner(Long profileId, Long adminId) {
        OwnerProfile profile = requireOwnerProfile(profileId);
        requirePending(profile.getStatus());
        approveUser(profile.getUser(), UserRole.OWNER, adminId);
        approveProfile(profile, adminId);
        RoleApplicationResponse response = mapOwner(ownerProfileRepository.save(profile));
        mailService.sendRoleApplicationApproved(profile.getUser(), UserRole.OWNER);
        return response;
    }

    private RoleApplicationResponse approveJockey(Long profileId, Long adminId) {
        JockeyProfile profile = requireJockeyProfile(profileId);
        requirePending(toRoleStatus(profile.getStatus()));
        approveUser(profile.getUser(), UserRole.JOCKEY, adminId);
        profile.setStatus(JockeyStatus.APPROVED);
        profile.setReviewReason(null);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
        RoleApplicationResponse response = mapJockey(jockeyProfileRepository.save(profile));
        mailService.sendRoleApplicationApproved(profile.getUser(), UserRole.JOCKEY);
        return response;
    }

    private RoleApplicationResponse approveSpectator(Long profileId, Long adminId) {
        SpectatorProfile profile = requireSpectatorProfile(profileId);
        requirePending(profile.getStatus());
        approveUser(profile.getUser(), UserRole.SPECTATOR, adminId);
        approveProfile(profile, adminId);
        RoleApplicationResponse response = mapSpectator(spectatorProfileRepository.save(profile));
        mailService.sendRoleApplicationApproved(profile.getUser(), UserRole.SPECTATOR);
        return response;
    }

    private RoleApplicationResponse approveReferee(Long profileId, Long adminId) {
        RefereeProfile profile = requireRefereeProfile(profileId);
        requirePending(profile.getStatus());
        approveUser(profile.getUser(), UserRole.REFEREE, adminId);
        approveProfile(profile, adminId);
        RoleApplicationResponse response = mapReferee(refereeProfileRepository.save(profile));
        mailService.sendRoleApplicationApproved(profile.getUser(), UserRole.REFEREE);
        return response;
    }

    private RoleApplicationResponse rejectOwner(Long profileId, Long adminId, String reason) {
        OwnerProfile profile = requireOwnerProfile(profileId);
        rejectUser(profile.getUser(), UserRole.OWNER, adminId, reason);
        rejectProfile(profile, adminId, reason);
        RoleApplicationResponse response = mapOwner(ownerProfileRepository.save(profile));
        mailService.sendRoleApplicationRejected(profile.getUser(), UserRole.OWNER, reason);
        return response;
    }

    private RoleApplicationResponse rejectJockey(Long profileId, Long adminId, String reason) {
        JockeyProfile profile = requireJockeyProfile(profileId);
        rejectUser(profile.getUser(), UserRole.JOCKEY, adminId, reason);
        profile.setStatus(JockeyStatus.REJECTED);
        profile.setReviewReason(reason);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
        RoleApplicationResponse response = mapJockey(jockeyProfileRepository.save(profile));
        mailService.sendRoleApplicationRejected(profile.getUser(), UserRole.JOCKEY, reason);
        return response;
    }

    private RoleApplicationResponse rejectSpectator(Long profileId, Long adminId, String reason) {
        SpectatorProfile profile = requireSpectatorProfile(profileId);
        rejectUser(profile.getUser(), UserRole.SPECTATOR, adminId, reason);
        rejectProfile(profile, adminId, reason);
        RoleApplicationResponse response = mapSpectator(spectatorProfileRepository.save(profile));
        mailService.sendRoleApplicationRejected(profile.getUser(), UserRole.SPECTATOR, reason);
        return response;
    }

    private RoleApplicationResponse rejectReferee(Long profileId, Long adminId, String reason) {
        RefereeProfile profile = requireRefereeProfile(profileId);
        rejectUser(profile.getUser(), UserRole.REFEREE, adminId, reason);
        rejectProfile(profile, adminId, reason);
        RoleApplicationResponse response = mapReferee(refereeProfileRepository.save(profile));
        mailService.sendRoleApplicationRejected(profile.getUser(), UserRole.REFEREE, reason);
        return response;
    }

    private List<RoleApplicationResponse> applicationsByRole(UserRole role, RoleApprovalStatus status) {
        if (status == RoleApprovalStatus.NONE) {
            return List.of();
        }
        return switch (role) {
            case OWNER -> (status == null
                    ? ownerProfileRepository.findAllByOrderByCreatedAtDesc()
                    : ownerProfileRepository.findByStatusOrderByCreatedAtDesc(status)).stream()
                    .map(this::mapOwner)
                    .toList();
            case JOCKEY -> (status == null
                    ? jockeyProfileRepository.findAllByOrderByCreatedAtDesc()
                    : jockeyProfileRepository.findByStatusOrderByCreatedAtDesc(toJockeyStatus(status))).stream()
                    .map(this::mapJockey)
                    .toList();
            case SPECTATOR -> (status == null
                    ? spectatorProfileRepository.findAllByOrderByCreatedAtDesc()
                    : spectatorProfileRepository.findByStatusOrderByCreatedAtDesc(status)).stream()
                    .map(this::mapSpectator)
                    .toList();
            case REFEREE -> (status == null
                    ? refereeProfileRepository.findAllByOrderByCreatedAtDesc()
                    : refereeProfileRepository.findByStatusOrderByCreatedAtDesc(status)).stream()
                    .map(this::mapReferee)
                    .toList();
            default -> throw new BadRequestException("Unsupported role application");
        };
    }

    private RoleApplicationResponse findByUserAndRole(Long userId, UserRole role) {
        return switch (role) {
            case OWNER -> ownerProfileRepository.findByUserId(userId)
                    .map(this::mapOwner)
                    .orElseThrow(() -> new ResourceNotFoundException("OwnerProfile", "userId", userId));
            case JOCKEY -> jockeyProfileRepository.findByUserId(userId)
                    .map(this::mapJockey)
                    .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", userId));
            case SPECTATOR -> spectatorProfileRepository.findByUserId(userId)
                    .map(this::mapSpectator)
                    .orElseThrow(() -> new ResourceNotFoundException("SpectatorProfile", "userId", userId));
            case REFEREE -> refereeProfileRepository.findByUserId(userId)
                    .map(this::mapReferee)
                    .orElseThrow(() -> new ResourceNotFoundException("RefereeProfile", "userId", userId));
            default -> throw new BadRequestException("Unsupported role application");
        };
    }

    private void requireCanSubmit(User user, UserRole targetRole) {
        requireApplicationRole(targetRole);
        if (user.getRole() != UserRole.USER) {
            throw new BadRequestException("Role already approved");
        }
        if (user.getRoleApprovalStatus() == RoleApprovalStatus.PENDING) {
            throw new BadRequestException("A role application is already pending");
        }
    }

    private void prepareUserPending(User user, UserRole pendingRole) {
        user.setPendingRole(pendingRole);
        user.setRoleApprovalStatus(RoleApprovalStatus.PENDING);
        user.setRoleReviewReason(null);
        user.setRoleReviewedBy(null);
        user.setRoleReviewedAt(null);
        user.setUpdatedBy(user.getUsername());
    }

    private void approveUser(User user, UserRole role, Long adminId) {
        user.setRole(role);
        user.setPendingRole(role);
        user.setRoleApprovalStatus(RoleApprovalStatus.APPROVED);
        user.setRoleReviewReason(null);
        user.setRoleReviewedBy(adminId);
        user.setRoleReviewedAt(LocalDateTime.now());
        user.setUpdatedBy("ADMIN:" + adminId);
        userRepository.save(user);
    }

    private void approveUserBySelf(User user, UserRole role) {
        user.setRole(role);
        user.setPendingRole(role);
        user.setRoleApprovalStatus(RoleApprovalStatus.APPROVED);
        user.setRoleReviewReason(null);
        user.setRoleReviewedBy(null);
        user.setRoleReviewedAt(LocalDateTime.now());
        user.setUpdatedBy(user.getUsername());
    }

    private void rejectUser(User user, UserRole role, Long adminId, String reason) {
        user.setRole(UserRole.USER);
        user.setPendingRole(role);
        user.setRoleApprovalStatus(RoleApprovalStatus.REJECTED);
        user.setRoleReviewReason(reason);
        user.setRoleReviewedBy(adminId);
        user.setRoleReviewedAt(LocalDateTime.now());
        user.setUpdatedBy("ADMIN:" + adminId);
        userRepository.save(user);
    }

    private void markProfilePending(OwnerProfile profile, User user) {
        profile.setStatus(RoleApprovalStatus.PENDING);
        profile.setReviewReason(null);
        profile.setReviewedBy(null);
        profile.setReviewedAt(null);
        profile.setUpdatedBy(user.getUsername());
    }

    private void markProfilePending(SpectatorProfile profile, User user) {
        profile.setStatus(RoleApprovalStatus.PENDING);
        profile.setReviewReason(null);
        profile.setReviewedBy(null);
        profile.setReviewedAt(null);
        profile.setUpdatedBy(user.getUsername());
    }

    private void markProfilePending(RefereeProfile profile, User user) {
        profile.setStatus(RoleApprovalStatus.PENDING);
        profile.setReviewReason(null);
        profile.setReviewedBy(null);
        profile.setReviewedAt(null);
        profile.setUpdatedBy(user.getUsername());
    }

    private void markJockeyPending(JockeyProfile profile, User user) {
        profile.setStatus(JockeyStatus.PENDING);
        profile.setReviewReason(null);
        profile.setReviewedBy(null);
        profile.setReviewedAt(null);
        profile.setUpdatedBy(user.getUsername());
    }

    private void approveProfile(OwnerProfile profile, Long adminId) {
        profile.setStatus(RoleApprovalStatus.APPROVED);
        profile.setReviewReason(null);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
    }

    private void approveProfile(SpectatorProfile profile, Long adminId) {
        profile.setStatus(RoleApprovalStatus.APPROVED);
        profile.setReviewReason(null);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
    }

    private void approveProfileByUser(SpectatorProfile profile, User user) {
        profile.setStatus(RoleApprovalStatus.APPROVED);
        profile.setReviewReason(null);
        profile.setReviewedBy(null);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy(user.getUsername());
    }

    private void approveProfile(RefereeProfile profile, Long adminId) {
        profile.setStatus(RoleApprovalStatus.APPROVED);
        profile.setReviewReason(null);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
    }

    private void rejectProfile(OwnerProfile profile, Long adminId, String reason) {
        profile.setStatus(RoleApprovalStatus.REJECTED);
        profile.setReviewReason(reason);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
    }

    private void rejectProfile(SpectatorProfile profile, Long adminId, String reason) {
        profile.setStatus(RoleApprovalStatus.REJECTED);
        profile.setReviewReason(reason);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
    }

    private void rejectProfile(RefereeProfile profile, Long adminId, String reason) {
        profile.setStatus(RoleApprovalStatus.REJECTED);
        profile.setReviewReason(reason);
        profile.setReviewedBy(adminId);
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy("ADMIN:" + adminId);
    }

    private void requireApplicationRole(UserRole role) {
        if (role == null || role == UserRole.USER || role == UserRole.ADMIN) {
            throw new BadRequestException("Unsupported role application");
        }
    }

    private UserRole requireUniquePendingApplicationRole(Long profileId) {
        List<UserRole> matches = new ArrayList<>();
        ownerProfileRepository.findById(profileId)
                .filter(profile -> profile.getStatus() == RoleApprovalStatus.PENDING)
                .ifPresent(profile -> matches.add(UserRole.OWNER));
        jockeyProfileRepository.findById(profileId)
                .filter(profile -> toRoleStatus(profile.getStatus()) == RoleApprovalStatus.PENDING)
                .ifPresent(profile -> matches.add(UserRole.JOCKEY));
        spectatorProfileRepository.findById(profileId)
                .filter(profile -> profile.getStatus() == RoleApprovalStatus.PENDING)
                .ifPresent(profile -> matches.add(UserRole.SPECTATOR));
        refereeProfileRepository.findById(profileId)
                .filter(profile -> profile.getStatus() == RoleApprovalStatus.PENDING)
                .ifPresent(profile -> matches.add(UserRole.REFEREE));
        if (matches.isEmpty()) {
            throw new ResourceNotFoundException("Pending role application", "profileId", profileId);
        }
        if (matches.size() > 1) {
            throw new BadRequestException("Profile id matches multiple pending role applications");
        }
        return matches.get(0);
    }

    private UserRole resolvePendingApplicationRole(Long profileId, UserRole role) {
        if (role == null) {
            return requireUniquePendingApplicationRole(profileId);
        }
        requireApplicationRole(role);
        return role;
    }

    private void requirePending(RoleApprovalStatus status) {
        if (status != RoleApprovalStatus.PENDING) {
            throw new BadRequestException("Only pending role applications can be approved");
        }
    }

    private String requireReason(AdminReviewRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Review reason is required");
        }
        return request.getReason();
    }

    private void requireUniqueJockeyLicense(String licenseNumber, Long userId) {
        if (jockeyProfileRepository.existsByLicenseNumberAndUserIdNot(licenseNumber, userId)) {
            throw new DuplicateResourceException("License number already exists");
        }
    }

    private void requireUniqueRefereeLicense(String licenseNumber, Long userId) {
        if (refereeProfileRepository.existsByLicenseNumberAndUserIdNot(licenseNumber, userId)) {
            throw new DuplicateResourceException("License number already exists");
        }
    }

    private JockeyStatus toJockeyStatus(RoleApprovalStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PENDING -> JockeyStatus.PENDING;
            case APPROVED -> JockeyStatus.APPROVED;
            case REJECTED -> JockeyStatus.REJECTED;
            case NONE -> throw new BadRequestException("Unsupported role application status");
        };
    }

    private RoleApprovalStatus toRoleStatus(JockeyStatus status) {
        return switch (status) {
            case PENDING -> RoleApprovalStatus.PENDING;
            case APPROVED -> RoleApprovalStatus.APPROVED;
            case REJECTED, SUSPENDED -> RoleApprovalStatus.REJECTED;
        };
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private User requireAdmin(Long adminId) {
        User admin = requireUser(adminId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedException("Only admins can review role applications");
        }
        return admin;
    }

    private OwnerProfile requireOwnerProfile(Long profileId) {
        return ownerProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerProfile", "id", profileId));
    }

    private JockeyProfile requireJockeyProfile(Long profileId) {
        return jockeyProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "id", profileId));
    }

    private SpectatorProfile requireSpectatorProfile(Long profileId) {
        return spectatorProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("SpectatorProfile", "id", profileId));
    }

    private RefereeProfile requireRefereeProfile(Long profileId) {
        return refereeProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeProfile", "id", profileId));
    }

    private RoleApplicationResponse baseUserApplication(User user, UserRole role, RoleApprovalStatus status) {
        return RoleApplicationResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(role)
                .status(status == null ? RoleApprovalStatus.NONE : status)
                .reviewReason(user.getRoleReviewReason())
                .reviewedBy(user.getRoleReviewedBy())
                .reviewedAt(user.getRoleReviewedAt())
                .build();
    }

    private RoleApplicationResponse mapOwner(OwnerProfile profile) {
        User user = profile.getUser();
        return baseBuilder(profile.getId(), user, UserRole.OWNER, profile.getStatus(),
                profile.getReviewReason(), profile.getReviewedBy(), profile.getReviewedAt(),
                profile.getCreatedAt(), profile.getUpdatedAt())
                .stableName(profile.getStableName())
                .experienceYears(profile.getExperienceYears())
                .address(profile.getAddress())
                .bio(profile.getBio())
                .verificationDocumentUrl(profile.getVerificationDocumentUrl())
                .build();
    }

    private RoleApplicationResponse mapSpectator(SpectatorProfile profile) {
        User user = profile.getUser();
        return baseBuilder(profile.getId(), user, UserRole.SPECTATOR, profile.getStatus(),
                profile.getReviewReason(), profile.getReviewedBy(), profile.getReviewedAt(),
                profile.getCreatedAt(), profile.getUpdatedAt())
                .displayName(profile.getDisplayName())
                .phone(profile.getPhone())
                .location(profile.getLocation())
                .favoriteHorseBreed(profile.getFavoriteHorseBreed())
                .bio(profile.getBio())
                .build();
    }

    private RoleApplicationResponse mapReferee(RefereeProfile profile) {
        User user = profile.getUser();
        return baseBuilder(profile.getId(), user, UserRole.REFEREE, profile.getStatus(),
                profile.getReviewReason(), profile.getReviewedBy(), profile.getReviewedAt(),
                profile.getCreatedAt(), profile.getUpdatedAt())
                .licenseNumber(profile.getLicenseNumber())
                .experienceYears(profile.getExperienceYears())
                .specialty(profile.getSpecialty())
                .certificationDocumentUrl(profile.getCertificationDocumentUrl())
                .bio(profile.getBio())
                .build();
    }

    private RoleApplicationResponse mapJockey(JockeyProfile profile) {
        User user = profile.getUser();
        return baseBuilder(profile.getId(), user, UserRole.JOCKEY, toRoleStatus(profile.getStatus()),
                profile.getReviewReason(), profile.getReviewedBy(), profile.getReviewedAt(),
                profile.getCreatedAt(), profile.getUpdatedAt())
                .licenseNumber(profile.getLicenseNumber())
                .experienceYears(profile.getExperienceYears())
                .heightCm(profile.getHeightCm())
                .weightKg(profile.getWeightKg())
                .bio(profile.getBio())
                .awards(profile.getAwards())
                .achievements(profile.getAchievements())
                .specialties(profile.getSpecialties())
                .avatarUrl(profile.getAvatarUrl())
                .licenseDocumentUrl(profile.getLicenseDocumentUrl())
                .build();
    }

    private RoleApplicationResponse.RoleApplicationResponseBuilder baseBuilder(
            Long profileId,
            User user,
            UserRole role,
            RoleApprovalStatus status,
            String reviewReason,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return RoleApplicationResponse.builder()
                .profileId(profileId)
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(role)
                .status(status)
                .reviewReason(reviewReason)
                .reviewedBy(reviewedBy)
                .reviewedAt(reviewedAt)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }
}
