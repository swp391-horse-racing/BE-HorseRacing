package com.minhthien.hoser_backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.request.*;
import com.minhthien.hoser_backend.dto.response.PublicBrandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceDistanceOptionResponse;
import com.minhthien.hoser_backend.dto.response.SystemSettingsResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.TwoFactorPolicy;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.SystemSettingsRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SystemSettingsServiceImpl implements SystemSettingsService {
    private static final String REFERENCE_TYPE = "SYSTEM_SETTINGS";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Integer>> INTEGER_LIST_TYPE = new TypeReference<>() {};
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("^(\\d+)\\s*m?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of("tournament", "race");

    private final SystemSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public SystemSettingsResponse getSettings() {
        return map(getCurrent());
    }

    @Override
    @Transactional
    public PublicBrandingResponse getPublicBranding() {
        SystemSettings settings = getCurrent();
        return new PublicBrandingResponse(settings.getSystemName(), settings.getPrimaryColor());
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateFees(Long adminId, SystemFeesSettingsRequest request) {
        User admin = requireAdmin(adminId);
        SystemSettings settings = getOrCreate();
        settings.setDefaultRegistrationFee(normalizeMoney(request.getDefaultRegistrationFee(), "Default registration fee"));
        settings.setLateCheckInFee(normalizePositiveMoney(request.getLateCheckInFee(), "Late check-in fee"));
        return save(admin, settings, "SYSTEM_FEES_UPDATED");
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateRules(Long adminId, SystemRulesSettingsRequest request) {
        User admin = requireAdmin(adminId);
        SystemSettings settings = getOrCreate();
        settings.setDefaultTournamentRules(request.getDefaultTournamentRules().trim());
        return save(admin, settings, "SYSTEM_RULES_UPDATED");
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateEmailTemplates(Long adminId, SystemEmailTemplatesSettingsRequest request) {
        User admin = requireAdmin(adminId);
        validateTemplate(request.getRegistrationOpenEmailSubject(), Set.of("tournament"));
        validateTemplate(request.getCheckInReminderEmailSubject(), Set.of("race"));
        validateTemplate(request.getRaceResultEmailSubject(), Set.of("race"));
        SystemSettings settings = getOrCreate();
        settings.setRegistrationOpenEmailSubject(request.getRegistrationOpenEmailSubject().trim());
        settings.setCheckInReminderEmailSubject(request.getCheckInReminderEmailSubject().trim());
        settings.setRaceResultEmailSubject(request.getRaceResultEmailSubject().trim());
        return save(admin, settings, "SYSTEM_EMAIL_TEMPLATES_UPDATED");
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateSecurity(Long adminId, SystemSecuritySettingsRequest request) {
        User admin = requireAdmin(adminId);
        SystemSettings settings = getOrCreate();
        settings.setTwoFactorPolicy(request.getTwoFactorPolicy());
        settings.setSessionDurationMinutes(request.getSessionDurationMinutes());
        return save(admin, settings, "SYSTEM_SECURITY_UPDATED");
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateBranding(Long adminId, SystemBrandingSettingsRequest request) {
        User admin = requireAdmin(adminId);
        SystemSettings settings = getOrCreate();
        settings.setSystemName(request.getSystemName().trim());
        settings.setPrimaryColor(request.getPrimaryColor().toUpperCase());
        return save(admin, settings, "SYSTEM_BRANDING_UPDATED");
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateRaceDistances(Long adminId, SystemRaceDistancesSettingsRequest request) {
        User admin = requireAdmin(adminId);
        List<Integer> distances = normalizeDistanceList(request == null ? null : request.getDistancesMeters());
        SystemSettings settings = getOrCreate();
        settings.setRaceDistancesMetersJson(writeDistances(distances));
        return save(admin, settings, "SYSTEM_RACE_DISTANCES_UPDATED");
    }

    @Override
    @Transactional
    @Cacheable(value = "systemSettings", key = "'singleton'")
    public SystemSettings getCurrent() {
        return getOrCreate();
    }

    @Override
    @Transactional
    public List<RaceDistanceOptionResponse> getRaceDistanceOptions() {
        return raceDistanceOptions(getCurrent());
    }

    @Override
    @Transactional
    public String normalizeRaceDistance(String distance) {
        if (distance == null || distance.isBlank()) {
            throw new BadRequestException("Race distance is required");
        }
        Matcher matcher = DISTANCE_PATTERN.matcher(distance.trim());
        if (!matcher.matches()) {
            throw new BadRequestException("Race distance must be a configured meter value");
        }
        Integer meters;
        try {
            meters = Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Race distance must be a valid meter value");
        }
        if (!readDistances(getCurrent()).contains(meters)) {
            throw new BadRequestException("Race distance is not configured");
        }
        return meters + "m";
    }

    @Override
    @Transactional
    @Cacheable(value = "systemSettings", key = "'requires2fa:' + #role.name()")
    public boolean requiresTwoFactor(UserRole role) {
        TwoFactorPolicy policy = getCurrent().getTwoFactorPolicy();
        return policy == TwoFactorPolicy.ALL_USERS
                || policy == TwoFactorPolicy.ADMIN_ONLY && role == UserRole.ADMIN;
    }

    @Override
    public String renderSubject(String template, String tournamentName, String raceName) {
        if (template == null) return "";
        return template
                .replace("{{tournament}}", tournamentName == null ? "" : tournamentName)
                .replace("{{race}}", raceName == null ? "" : raceName);
    }

    private SystemSettings getOrCreate() {
        return settingsRepository.findById(SystemSettings.SINGLETON_ID)
                .orElseGet(() -> settingsRepository.save(defaultSettings()));
    }

    private SystemSettings defaultSettings() {
        return SystemSettings.builder()
                .id(SystemSettings.SINGLETON_ID)
                .defaultRegistrationFee(SystemSettings.DEFAULT_REGISTRATION_FEE)
                .lateCheckInFee(SystemSettings.DEFAULT_LATE_CHECK_IN_FEE)
                .defaultTournamentRules(SystemSettings.DEFAULT_RULES)
                .registrationOpenEmailSubject(SystemSettings.DEFAULT_REGISTRATION_OPEN_SUBJECT)
                .checkInReminderEmailSubject(SystemSettings.DEFAULT_CHECK_IN_REMINDER_SUBJECT)
                .raceResultEmailSubject(SystemSettings.DEFAULT_RACE_RESULT_SUBJECT)
                .twoFactorPolicy(SystemSettings.DEFAULT_TWO_FACTOR_POLICY)
                .sessionDurationMinutes(SystemSettings.DEFAULT_SESSION_DURATION_MINUTES)
                .systemName(SystemSettings.DEFAULT_SYSTEM_NAME)
                .primaryColor(SystemSettings.DEFAULT_PRIMARY_COLOR)
                .raceDistancesMetersJson(SystemSettings.DEFAULT_RACE_DISTANCES_METERS_JSON)
                .build();
    }

    private SystemSettingsResponse save(User admin, SystemSettings settings, String action) {
        settings.setUpdatedBy(admin.getUsername());
        SystemSettings saved = settingsRepository.save(settings);
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(admin.getId())
                .action(action)
                .referenceType(REFERENCE_TYPE)
                .referenceId(String.valueOf(SystemSettings.SINGLETON_ID))
                .reason("System settings updated")
                .metadata("updatedBy=" + admin.getUsername())
                .build());
        return map(saved);
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Only admins can update system settings");
        }
        return admin;
    }

    private BigDecimal normalizeMoney(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(label + " must not be negative");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePositiveMoney(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(label + " must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateTemplate(String template, Set<String> requiredPlaceholders) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        Set<String> found = new java.util.HashSet<>();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!ALLOWED_PLACEHOLDERS.contains(placeholder)) {
                throw new BadRequestException("Unsupported email placeholder: {{" + placeholder + "}}");
            }
            found.add(placeholder);
        }
        if (!found.containsAll(requiredPlaceholders)) {
            throw new BadRequestException("Email subject is missing required placeholder: {{"
                    + requiredPlaceholders.iterator().next() + "}}");
        }
    }

    private List<Integer> normalizeDistanceList(List<Integer> distances) {
        if (distances == null || distances.isEmpty()) {
            throw new BadRequestException("Race distances are required");
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer distance : distances) {
            if (distance == null || distance <= 0) {
                throw new BadRequestException("Race distance must be greater than zero");
            }
            if (!sorted.add(distance)) {
                throw new BadRequestException("Race distances must be unique");
            }
        }
        return List.copyOf(sorted);
    }

    private List<Integer> readDistances(SystemSettings settings) {
        try {
            String source = settings.getRaceDistancesMetersJson();
            if (source == null || source.isBlank()) {
                source = SystemSettings.DEFAULT_RACE_DISTANCES_METERS_JSON;
            }
            List<Integer> distances = OBJECT_MAPPER.readValue(source, INTEGER_LIST_TYPE);
            return normalizeDistanceList(distances);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BadRequestException("Race distance settings are invalid");
        }
    }

    private String writeDistances(List<Integer> distances) {
        try {
            return OBJECT_MAPPER.writeValueAsString(distances);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Race distance settings are invalid");
        }
    }

    private List<RaceDistanceOptionResponse> raceDistanceOptions(SystemSettings settings) {
        return readDistances(settings).stream()
                .map(meters -> RaceDistanceOptionResponse.builder()
                        .meters(meters)
                        .label(meters + "m")
                        .build())
                .toList();
    }

    private SystemSettingsResponse map(SystemSettings settings) {
        return SystemSettingsResponse.builder()
                .defaultRegistrationFee(settings.getDefaultRegistrationFee())
                .lateCheckInFee(settings.getLateCheckInFee())
                .defaultTournamentRules(settings.getDefaultTournamentRules())
                .registrationOpenEmailSubject(settings.getRegistrationOpenEmailSubject())
                .checkInReminderEmailSubject(settings.getCheckInReminderEmailSubject())
                .raceResultEmailSubject(settings.getRaceResultEmailSubject())
                .twoFactorPolicy(settings.getTwoFactorPolicy())
                .sessionDurationMinutes(settings.getSessionDurationMinutes())
                .systemName(settings.getSystemName())
                .primaryColor(settings.getPrimaryColor())
                .raceDistances(raceDistanceOptions(settings))
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }
}
