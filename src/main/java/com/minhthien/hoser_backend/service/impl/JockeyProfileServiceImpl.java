package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileUpdateRequest;
import com.minhthien.hoser_backend.dto.response.JockeyDetailPerformanceResponse;
import com.minhthien.hoser_backend.dto.response.JockeyProfileResponse;
import com.minhthien.hoser_backend.dto.response.JockeyRaceHistoryResponse;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceResult;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.JockeyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JockeyProfileServiceImpl implements JockeyProfileService {
    private static final String JOCKEY_AVATAR_FOLDER = "hoser/jockeys/avatars";
    private static final String JOCKEY_ACHIEVEMENTS_FOLDER = "hoser/jockeys/achievements";
    private static final String JOCKEY_LICENSE_DOCUMENT_FOLDER = "hoser/jockeys/license-documents";

    private final JockeyProfileRepository jockeyProfileRepository;
    private final UserRepository userRepository;
    private final CloudinaryUploadService cloudinaryUploadService;
    private final RaceResultRepository raceResultRepository;

    @Override
    @Transactional(readOnly = true)
    public JockeyProfileResponse getMyProfile(Long jockeyId) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can view jockey profile");
        return jockeyProfileRepository.findByUserId(jockeyId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", jockeyId));
    }

    @Override
    @Transactional
    public JockeyProfileResponse createMyProfile(Long jockeyId, JockeyProfileRequest request,
                                                 MultipartFile avatar, MultipartFile licenseDocument) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can manage jockey profile");
        if (jockeyProfileRepository.findByUserId(jockeyId).isPresent()) {
            throw new DuplicateResourceException("Jockey profile already exists");
        }
        requireUniqueLicenseNumber(request.getLicenseNumber(), jockeyId);

        JockeyProfile profile = JockeyProfile.builder()
                .user(jockey)
                .createdBy(jockey.getUsername())
                .build();
        applyRequest(profile, request);
        applyUploadedFiles(profile, avatar, request.getAchievements(), licenseDocument);
        resetForReview(profile, jockey);
        return mapToResponse(jockeyProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public JockeyProfileResponse updateMyProfile(Long jockeyId, JockeyProfileUpdateRequest request,
                                                 MultipartFile avatar, MultipartFile licenseDocument) {
        User jockey = requireUser(jockeyId);
        requireRole(jockey, UserRole.JOCKEY, "Only jockeys can manage jockey profile");
        requireUpdateRequest(request);
        JockeyProfile profile = jockeyProfileRepository.findByUserId(jockeyId)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", jockeyId));
        if (profile.getStatus() == JockeyStatus.SUSPENDED) {
            throw new BadRequestException("Suspended jockey profile cannot be updated");
        }
        if (request.getLicenseNumber() != null) {
            if (!hasText(request.getLicenseNumber())) {
                throw new BadRequestException("License number is required");
            }
            requireUniqueLicenseNumber(request.getLicenseNumber(), jockeyId);
        }

        applyUpdateRequest(profile, request);
        applyUploadedFiles(profile, avatar, request.getAchievements(), licenseDocument);
        resetForReview(profile, jockey);
        return mapToResponse(jockeyProfileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyProfileResponse> getAvailableJockeys() {
        return jockeyProfileRepository.findByStatusOrderByCreatedAtDesc(JockeyStatus.APPROVED).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JockeyProfileResponse getApprovedJockeyProfile(Long jockeyId) {
        JockeyProfile profile = jockeyProfileRepository.findByUserId(jockeyId)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "userId", jockeyId));
        if (profile.getStatus() != JockeyStatus.APPROVED) {
            throw new ResourceNotFoundException("Approved jockey profile not found with userId: '" + jockeyId + "'");
        }
        return mapToResponse(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JockeyProfileResponse> getAdminJockeyProfiles(JockeyStatus status) {
        List<JockeyProfile> profiles = status == null
                ? jockeyProfileRepository.findAllByOrderByCreatedAtDesc()
                : jockeyProfileRepository.findByStatusOrderByCreatedAtDesc(status);
        return profiles.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public JockeyProfileResponse approveJockeyProfile(Long profileId, Long adminId) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can review jockey profiles");
        JockeyProfile profile = requireProfile(profileId);
        review(profile, JockeyStatus.APPROVED, admin, null);
        return mapToResponse(jockeyProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public JockeyProfileResponse rejectJockeyProfile(Long profileId, Long adminId, AdminReviewRequest request) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can review jockey profiles");
        JockeyProfile profile = requireProfile(profileId);
        review(profile, JockeyStatus.REJECTED, admin, requireReason(request));
        return mapToResponse(jockeyProfileRepository.save(profile));
    }

    @Override
    @Transactional
    public JockeyProfileResponse suspendJockeyProfile(Long profileId, Long adminId, AdminReviewRequest request) {
        User admin = requireUser(adminId);
        requireRole(admin, UserRole.ADMIN, "Only admins can review jockey profiles");
        JockeyProfile profile = requireProfile(profileId);
        review(profile, JockeyStatus.SUSPENDED, admin, requireReason(request));
        return mapToResponse(jockeyProfileRepository.save(profile));
    }

    private void applyRequest(JockeyProfile profile, JockeyProfileRequest request) {
        profile.setLicenseNumber(request.getLicenseNumber());
        profile.setExperienceYears(request.getExperienceYears());
        profile.setHeightCm(request.getHeightCm());
        profile.setWeightKg(request.getWeightKg());
        profile.setBio(request.getBio());
        profile.setAwards(request.getAwards());
        profile.setSpecialties(request.getSpecialties());
    }

    private void applyUpdateRequest(JockeyProfile profile, JockeyProfileUpdateRequest request) {
        if (request.getLicenseNumber() != null) {
            profile.setLicenseNumber(request.getLicenseNumber());
        }
        if (request.getExperienceYears() != null) {
            profile.setExperienceYears(request.getExperienceYears());
        }
        if (request.getHeightCm() != null) {
            profile.setHeightCm(request.getHeightCm());
        }
        if (request.getWeightKg() != null) {
            profile.setWeightKg(request.getWeightKg());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getAwards() != null) {
            profile.setAwards(request.getAwards());
        }
        if (request.getSpecialties() != null) {
            profile.setSpecialties(request.getSpecialties());
        }
    }

    private void applyUploadedFiles(JockeyProfile profile, MultipartFile avatar, MultipartFile achievements,
                                    MultipartFile licenseDocument) {
        if (avatar != null) {
            profile.setAvatarUrl(cloudinaryUploadService.uploadImage(avatar, JOCKEY_AVATAR_FOLDER));
        }
        if (achievements != null) {
            profile.setAchievements(cloudinaryUploadService.uploadImage(achievements, JOCKEY_ACHIEVEMENTS_FOLDER));
        }
        if (licenseDocument != null) {
            profile.setLicenseDocumentUrl(cloudinaryUploadService.uploadDocument(
                    licenseDocument, JOCKEY_LICENSE_DOCUMENT_FOLDER));
        }
    }

    private void resetForReview(JockeyProfile profile, User jockey) {
        profile.setStatus(JockeyStatus.PENDING);
        profile.setReviewReason(null);
        profile.setReviewedBy(null);
        profile.setReviewedAt(null);
        profile.setUpdatedBy(jockey.getUsername());
    }

    private void requireUniqueLicenseNumber(String licenseNumber, Long jockeyId) {
        if (jockeyProfileRepository.existsByLicenseNumberAndUserIdNot(licenseNumber, jockeyId)) {
            throw new DuplicateResourceException("License number already exists");
        }
    }

    private void review(JockeyProfile profile, JockeyStatus status, User admin, String reason) {
        profile.setStatus(status);
        profile.setReviewReason(reason);
        profile.setReviewedBy(admin.getId());
        profile.setReviewedAt(LocalDateTime.now());
        profile.setUpdatedBy(admin.getUsername());
    }

    private String requireReason(AdminReviewRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Review reason is required");
        }
        return request.getReason();
    }

    private void requireUpdateRequest(JockeyProfileUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Jockey profile request is required");
        }
    }

    private JockeyProfile requireProfile(Long profileId) {
        return jockeyProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("JockeyProfile", "id", profileId));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void requireRole(User user, UserRole role, String message) {
        if (user.getRole() != role) {
            throw new UnauthorizedException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private JockeyProfileResponse mapToResponse(JockeyProfile profile) {
        Long jockeyId = profile.getUser().getId();
        List<RaceResult> results = jockeyId == null
                ? List.of()
                : raceResultRepository.findByJockeyIdOrderByRaceScheduledStartAtDesc(jockeyId);
        return JockeyProfileResponse.builder()
                .id(profile.getId())
                .userId(jockeyId)
                .username(profile.getUser().getUsername())
                .fullName(profile.getUser().getFullName())
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
                .status(profile.getStatus())
                .reviewReason(profile.getReviewReason())
                .reviewedBy(profile.getReviewedBy())
                .reviewedAt(profile.getReviewedAt())
                .performance(mapPerformance(results))
                .raceHistory(results.stream().map(this::mapRaceHistory).toList())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private JockeyDetailPerformanceResponse mapPerformance(List<RaceResult> results) {
        int totalRaces = results.size();
        int wins = Math.toIntExact(results.stream()
                .filter(result -> Integer.valueOf(1).equals(result.getRank()))
                .count());
        Map<String, Long> rankCounts = results.stream()
                .map(RaceResult::getRank)
                .filter(rank -> rank != null)
                .collect(Collectors.groupingBy(
                        String::valueOf,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        return JockeyDetailPerformanceResponse.builder()
                .totalRaces(totalRaces)
                .wins(wins)
                .winRate(calculateWinRate(wins, totalRaces))
                .rankCounts(Collections.unmodifiableMap(rankCounts))
                .build();
    }

    private BigDecimal calculateWinRate(int wins, int totalRaces) {
        if (totalRaces == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(wins)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRaces), 2, RoundingMode.HALF_UP);
    }

    private JockeyRaceHistoryResponse mapRaceHistory(RaceResult result) {
        Race race = result.getRace();
        Tournament tournament = race.getTournament();
        Horse horse = result.getHorse();
        return JockeyRaceHistoryResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getName())
                .raceId(race.getId())
                .raceName(race.getName())
                .scheduledStartAt(race.getScheduledStartAt())
                .horseId(horse.getId())
                .horseName(horse.getName())
                .rank(result.getRank())
                .status(result.getStatus())
                .finishTimeMillis(result.getFinishTimeMillis())
                .finalizedAt(result.getFinalizedAt())
                .build();
    }
}
