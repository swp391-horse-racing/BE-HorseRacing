package com.minhthien.hoser_backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhthien.hoser_backend.dto.request.*;
import com.minhthien.hoser_backend.dto.response.PublicBrandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceDistanceOptionResponse;
import com.minhthien.hoser_backend.dto.response.SystemSettingsResponse;
import com.minhthien.hoser_backend.dto.response.ViolationPenaltyRuleResponse;
import com.minhthien.hoser_backend.dto.response.ViolationTypeOptionResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.TwoFactorPolicy;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.ViolationResultAction;
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
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final TypeReference<List<ViolationPenaltyRuleRequest>> VIOLATION_RULE_LIST_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<ViolationTypeOptionRequest>> VIOLATION_TYPE_LIST_TYPE =
            new TypeReference<>() {};
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("^(\\d+)\\s*m?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_CLEANUP_PATTERN = Pattern.compile("[^A-Z0-9]+");
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
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateViolationRules(Long adminId, SystemViolationRulesSettingsRequest request) {
        User admin = requireAdmin(adminId);
        List<ViolationPenaltyRuleResponse> rules = normalizeViolationRules(request == null ? null : request.getRules());
        SystemSettings settings = getOrCreate();
        settings.setViolationPenaltyRulesJson(writeViolationRules(rules));
        return save(admin, settings, "SYSTEM_VIOLATION_RULES_UPDATED");
    }

    @Override
    @Transactional
    @CacheEvict(value = "systemSettings", allEntries = true)
    public SystemSettingsResponse updateViolationTypes(Long adminId, SystemViolationTypesSettingsRequest request) {
        User admin = requireAdmin(adminId);
        List<ViolationTypeOptionResponse> types = normalizeViolationTypes(request == null ? null : request.getTypes());
        SystemSettings settings = getOrCreate();
        settings.setViolationTypeOptionsJson(writeViolationTypes(types));
        return save(admin, settings, "SYSTEM_VIOLATION_TYPES_UPDATED");
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
    public List<ViolationPenaltyRuleResponse> getViolationPenaltyRules() {
        return violationPenaltyRules(getCurrent());
    }

    @Override
    @Transactional
    public List<ViolationTypeOptionResponse> getViolationTypes() {
        return violationTypes(getCurrent());
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
    public ViolationTypeOptionResponse requireActiveViolationType(String typeCode) {
        String normalizedCode = normalizeExistingCode(typeCode);
        return violationTypes(getCurrent()).stream()
                .filter(type -> Boolean.TRUE.equals(type.getActive()))
                .filter(type -> type.getCode().equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Violation type is not configured or active"));
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
                .violationPenaltyRulesJson(SystemSettings.DEFAULT_VIOLATION_PENALTY_RULES_JSON)
                .violationTypeOptionsJson(SystemSettings.DEFAULT_VIOLATION_TYPE_OPTIONS_JSON)
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

    private List<ViolationPenaltyRuleResponse> normalizeViolationRules(List<ViolationPenaltyRuleRequest> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BadRequestException("Violation penalty rules are required");
        }
        Map<RaceViolationSeverity, ViolationPenaltyRuleResponse> bySeverity =
                new EnumMap<>(RaceViolationSeverity.class);
        for (ViolationPenaltyRuleRequest rule : rules) {
            if (rule == null || rule.getSeverity() == null || rule.getResultAction() == null) {
                throw new BadRequestException("Violation penalty rule severity and action are required");
            }
            if (bySeverity.containsKey(rule.getSeverity())) {
                throw new BadRequestException("Duplicate violation penalty rule for " + rule.getSeverity());
            }
            long timePenaltyMillis = rule.getTimePenaltyMillis() == null ? 0L : rule.getTimePenaltyMillis();
            if (timePenaltyMillis < 0) {
                throw new BadRequestException("Violation time penalty must not be negative");
            }
            if (rule.getResultAction() == ViolationResultAction.TIME_PENALTY && timePenaltyMillis <= 0) {
                throw new BadRequestException("Time penalty action requires a positive time penalty");
            }
            if (rule.getResultAction() != ViolationResultAction.TIME_PENALTY) {
                timePenaltyMillis = 0L;
            }
            bySeverity.put(rule.getSeverity(), ViolationPenaltyRuleResponse.builder()
                    .severity(rule.getSeverity())
                    .resultAction(rule.getResultAction())
                    .timePenaltyMillis(timePenaltyMillis)
                    .build());
        }
        if (!bySeverity.keySet().equals(EnumSet.allOf(RaceViolationSeverity.class))) {
            throw new BadRequestException("Violation penalty rules must include every severity");
        }
        return EnumSet.allOf(RaceViolationSeverity.class).stream()
                .map(bySeverity::get)
                .toList();
    }

    private List<ViolationPenaltyRuleResponse> readViolationRules(SystemSettings settings) {
        try {
            String source = settings.getViolationPenaltyRulesJson();
            if (source == null || source.isBlank()) {
                source = SystemSettings.DEFAULT_VIOLATION_PENALTY_RULES_JSON;
            }
            List<ViolationPenaltyRuleRequest> rules = OBJECT_MAPPER.readValue(source, VIOLATION_RULE_LIST_TYPE);
            return normalizeViolationRules(rules);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BadRequestException("Violation penalty rule settings are invalid");
        }
    }

    private String writeViolationRules(List<ViolationPenaltyRuleResponse> rules) {
        try {
            return OBJECT_MAPPER.writeValueAsString(rules);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Violation penalty rule settings are invalid");
        }
    }

    private List<ViolationTypeOptionResponse> normalizeViolationTypes(List<ViolationTypeOptionRequest> types) {
        if (types == null || types.isEmpty()) {
            throw new BadRequestException("Violation types are required");
        }
        Set<String> labels = new HashSet<>();
        Set<String> codes = new HashSet<>();
        List<ViolationTypeOptionResponse> normalized = new java.util.ArrayList<>();
        int activeCount = 0;
        for (ViolationTypeOptionRequest type : types) {
            if (type == null || type.getLabel() == null || type.getLabel().isBlank()) {
                throw new BadRequestException("Violation type label is required");
            }
            String label = type.getLabel().trim();
            if (label.length() > 100) {
                throw new BadRequestException("Violation type label must be at most 100 characters");
            }
            String labelKey = label.toLowerCase(java.util.Locale.ROOT);
            if (!labels.add(labelKey)) {
                throw new BadRequestException("Duplicate violation type label: " + label);
            }
            String code = type.getCode() == null || type.getCode().isBlank()
                    ? generateViolationTypeCode(label, codes)
                    : normalizeExistingCode(type.getCode());
            if (!codes.add(code)) {
                throw new BadRequestException("Duplicate violation type code: " + code);
            }
            boolean active = type.getActive() == null || type.getActive();
            if (active) {
                activeCount++;
            }
            normalized.add(ViolationTypeOptionResponse.builder()
                    .code(code)
                    .label(label)
                    .active(active)
                    .build());
        }
        if (activeCount == 0) {
            throw new BadRequestException("At least one violation type must be active");
        }
        return normalized;
    }

    private List<ViolationTypeOptionResponse> readViolationTypes(SystemSettings settings) {
        try {
            String source = settings.getViolationTypeOptionsJson();
            if (source == null || source.isBlank()) {
                source = SystemSettings.DEFAULT_VIOLATION_TYPE_OPTIONS_JSON;
            }
            List<ViolationTypeOptionRequest> types = OBJECT_MAPPER.readValue(source, VIOLATION_TYPE_LIST_TYPE);
            return normalizeViolationTypes(types);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BadRequestException("Violation type settings are invalid");
        }
    }

    private String writeViolationTypes(List<ViolationTypeOptionResponse> types) {
        try {
            return OBJECT_MAPPER.writeValueAsString(types);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Violation type settings are invalid");
        }
    }

    private String normalizeExistingCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Violation type code is required");
        }
        String normalized = CODE_CLEANUP_PATTERN.matcher(code.trim().toUpperCase(java.util.Locale.ROOT))
                .replaceAll("_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new BadRequestException("Violation type code is required");
        }
        if (normalized.length() > 80) {
            throw new BadRequestException("Violation type code must be at most 80 characters");
        }
        return normalized;
    }

    private String generateViolationTypeCode(String label, Set<String> usedCodes) {
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replace("đ", "d")
                .replace("Đ", "D")
                .replaceAll("\\p{M}", "");
        String base = CODE_CLEANUP_PATTERN.matcher(ascii.toUpperCase(java.util.Locale.ROOT))
                .replaceAll("_")
                .replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "VIOLATION_TYPE";
        }
        if (base.length() > 72) {
            base = base.substring(0, 72).replaceAll("_+$", "");
        }
        String candidate = base;
        int suffix = 2;
        while (usedCodes.contains(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private List<RaceDistanceOptionResponse> raceDistanceOptions(SystemSettings settings) {
        return readDistances(settings).stream()
                .map(meters -> RaceDistanceOptionResponse.builder()
                        .meters(meters)
                        .label(meters + "m")
                        .build())
                .toList();
    }

    private List<ViolationPenaltyRuleResponse> violationPenaltyRules(SystemSettings settings) {
        return readViolationRules(settings);
    }

    private List<ViolationTypeOptionResponse> violationTypes(SystemSettings settings) {
        return readViolationTypes(settings);
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
                .violationPenaltyRules(violationPenaltyRules(settings))
                .violationTypes(violationTypes(settings))
                .createdAt(settings.getCreatedAt())
                .updatedAt(settings.getUpdatedAt())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }
}
