package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.SystemEmailTemplatesSettingsRequest;
import com.minhthien.hoser_backend.dto.request.SystemFeesSettingsRequest;
import com.minhthien.hoser_backend.dto.request.SystemRaceDistancesSettingsRequest;
import com.minhthien.hoser_backend.dto.request.SystemViolationRulesSettingsRequest;
import com.minhthien.hoser_backend.dto.request.SystemViolationTypesSettingsRequest;
import com.minhthien.hoser_backend.dto.request.ViolationPenaltyRuleRequest;
import com.minhthien.hoser_backend.dto.request.ViolationTypeOptionRequest;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RaceViolationSeverity;
import com.minhthien.hoser_backend.enums.TwoFactorPolicy;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.ViolationResultAction;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.SystemSettingsRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceImplTest {
    @Mock private SystemSettingsRepository settingsRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditLogRepository auditLogRepository;

    @InjectMocks
    private SystemSettingsServiceImpl service;

    @Test
    void updateFeesNormalizesValuesAndWritesAuditLog() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        SystemSettings settings = settings();
        SystemFeesSettingsRequest request = new SystemFeesSettingsRequest();
        request.setDefaultRegistrationFee(new BigDecimal("5000000"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        var response = service.updateFees(1L, request);

        assertEquals(new BigDecimal("5000000.00"), response.getDefaultRegistrationFee());
        assertEquals("admin", response.getUpdatedBy());
        verify(auditLogRepository).save(any());
    }

    @Test
    void emailTemplatesRejectUnknownAndMissingPlaceholders() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        SystemEmailTemplatesSettingsRequest request = new SystemEmailTemplatesSettingsRequest();
        request.setRegistrationOpenEmailSubject("Open {{unknown}}");
        request.setCheckInReminderEmailSubject("Reminder {{race}}");
        request.setRaceResultEmailSubject("Result {{race}}");

        assertThrows(BadRequestException.class, () -> service.updateEmailTemplates(1L, request));

        request.setRegistrationOpenEmailSubject("Open registration");
        assertThrows(BadRequestException.class, () -> service.updateEmailTemplates(1L, request));
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void defaultTwoFactorPolicyIsDisabled() {
        SystemSettings settings = settings();
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertFalse(service.requiresTwoFactor(UserRole.ADMIN));
        assertFalse(service.requiresTwoFactor(UserRole.OWNER));
    }

    @Test
    void adminOnlyTwoFactorPolicyRequiresAdminOnly() {
        SystemSettings settings = settings();
        settings.setTwoFactorPolicy(TwoFactorPolicy.ADMIN_ONLY);
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertTrue(service.requiresTwoFactor(UserRole.ADMIN));
        assertFalse(service.requiresTwoFactor(UserRole.OWNER));
    }

    @Test
    void updateRaceDistancesSortsValuesAndReturnsLabels() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        SystemSettings settings = settings();
        SystemRaceDistancesSettingsRequest request = new SystemRaceDistancesSettingsRequest();
        request.setDistancesMeters(List.of(1500, 1000, 1200));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        var response = service.updateRaceDistances(1L, request);

        assertEquals(List.of(1000, 1200, 1500), response.getRaceDistances().stream()
                .map(option -> option.getMeters())
                .toList());
        assertEquals("1000m", response.getRaceDistances().get(0).getLabel());
        assertEquals("[1000,1200,1500]", settings.getRaceDistancesMetersJson());
        verify(auditLogRepository).save(any());
    }

    @Test
    void updateRaceDistancesRejectsEmptyInvalidAndDuplicateValues() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        SystemRaceDistancesSettingsRequest request = new SystemRaceDistancesSettingsRequest();
        request.setDistancesMeters(List.of());
        assertThrows(BadRequestException.class, () -> service.updateRaceDistances(1L, request));

        request.setDistancesMeters(List.of(1000, 0));
        assertThrows(BadRequestException.class, () -> service.updateRaceDistances(1L, request));

        request.setDistancesMeters(List.of(1000, 1000));
        assertThrows(BadRequestException.class, () -> service.updateRaceDistances(1L, request));
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void normalizeRaceDistanceAcceptsConfiguredMetersAndLabelsOnly() {
        SystemSettings settings = settings();
        settings.setRaceDistancesMetersJson("[1000,1200,1500]");
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertEquals("1000m", service.normalizeRaceDistance("1000"));
        assertEquals("1200m", service.normalizeRaceDistance("1200m"));
        assertThrows(BadRequestException.class, () -> service.normalizeRaceDistance("1300m"));
    }

    @Test
    void updateViolationRulesNormalizesAndStoresJson() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        SystemSettings settings = settings();
        SystemViolationRulesSettingsRequest request = violationRulesRequest(
                rule(RaceViolationSeverity.WARNING, ViolationResultAction.NONE, 5000L),
                rule(RaceViolationSeverity.MINOR, ViolationResultAction.TIME_PENALTY, 3000L),
                rule(RaceViolationSeverity.MAJOR, ViolationResultAction.TIME_PENALTY, 10000L),
                rule(RaceViolationSeverity.DISQUALIFICATION, ViolationResultAction.DISQUALIFY, 2000L)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        var response = service.updateViolationRules(1L, request);

        assertEquals(4, response.getViolationPenaltyRules().size());
        assertEquals(0L, response.getViolationPenaltyRules().get(0).getTimePenaltyMillis());
        assertEquals(3000L, response.getViolationPenaltyRules().get(1).getTimePenaltyMillis());
        assertTrue(settings.getViolationPenaltyRulesJson().contains("\"MINOR\""));
        verify(auditLogRepository).save(any());
    }

    @Test
    void updateViolationRulesRejectsTimePenaltyWithoutPositiveMillis() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        SystemViolationRulesSettingsRequest request = violationRulesRequest(
                rule(RaceViolationSeverity.WARNING, ViolationResultAction.NONE, 0L),
                rule(RaceViolationSeverity.MINOR, ViolationResultAction.TIME_PENALTY, 0L),
                rule(RaceViolationSeverity.MAJOR, ViolationResultAction.TIME_PENALTY, 10000L),
                rule(RaceViolationSeverity.DISQUALIFICATION, ViolationResultAction.DISQUALIFY, 0L)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class, () -> service.updateViolationRules(1L, request));
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void getSettingsReturnsDefaultViolationTypes() {
        SystemSettings settings = settings();
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        var response = service.getSettings();

        assertEquals(6, response.getViolationTypes().size());
        assertEquals("FALSE_START", response.getViolationTypes().get(0).getCode());
        assertEquals("Xuất phát sai", response.getViolationTypes().get(0).getLabel());
        assertTrue(response.getViolationTypes().get(0).getActive());
    }

    @Test
    void updateViolationTypesAcceptsCustomTypesAndGeneratedCodes() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        SystemSettings settings = settings();
        SystemViolationTypesSettingsRequest request = violationTypesRequest(
                type("FALSE_START", "Xuất phát sai", true),
                type(null, "Cản đường đua", true),
                type("OLD_TYPE", "Loại cũ", false)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        var response = service.updateViolationTypes(1L, request);

        assertEquals(3, response.getViolationTypes().size());
        assertEquals("CAN_DUONG_DUA", response.getViolationTypes().get(1).getCode());
        assertEquals("Cản đường đua", response.getViolationTypes().get(1).getLabel());
        assertFalse(response.getViolationTypes().get(2).getActive());
        assertTrue(settings.getViolationTypeOptionsJson().contains("CAN_DUONG_DUA"));
        verify(auditLogRepository).save(any());
    }

    @Test
    void updateViolationTypesRejectsInvalidLists() {
        User admin = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(BadRequestException.class, () ->
                service.updateViolationTypes(1L, violationTypesRequest(type("ONE", "Một", false))));
        assertThrows(BadRequestException.class, () ->
                service.updateViolationTypes(1L, violationTypesRequest(type("ONE", "", true))));
        assertThrows(BadRequestException.class, () ->
                service.updateViolationTypes(1L, violationTypesRequest(
                        type("ONE", "Trùng", true),
                        type("TWO", "trùng", true))));
        assertThrows(BadRequestException.class, () ->
                service.updateViolationTypes(1L, violationTypesRequest(
                        type("DUPLICATE", "Một", true),
                        type("duplicate", "Hai", true))));
        verify(settingsRepository, never()).save(any());
    }

    private SystemViolationRulesSettingsRequest violationRulesRequest(ViolationPenaltyRuleRequest... rules) {
        SystemViolationRulesSettingsRequest request = new SystemViolationRulesSettingsRequest();
        request.setRules(List.of(rules));
        return request;
    }

    private ViolationPenaltyRuleRequest rule(RaceViolationSeverity severity, ViolationResultAction action,
                                             Long timePenaltyMillis) {
        ViolationPenaltyRuleRequest request = new ViolationPenaltyRuleRequest();
        request.setSeverity(severity);
        request.setResultAction(action);
        request.setTimePenaltyMillis(timePenaltyMillis);
        return request;
    }

    private SystemViolationTypesSettingsRequest violationTypesRequest(ViolationTypeOptionRequest... types) {
        SystemViolationTypesSettingsRequest request = new SystemViolationTypesSettingsRequest();
        request.setTypes(List.of(types));
        return request;
    }

    private ViolationTypeOptionRequest type(String code, String label, boolean active) {
        ViolationTypeOptionRequest request = new ViolationTypeOptionRequest();
        request.setCode(code);
        request.setLabel(label);
        request.setActive(active);
        return request;
    }

    private SystemSettings settings() {
        return SystemSettings.builder()
                .id(SystemSettings.SINGLETON_ID)
                .defaultRegistrationFee(SystemSettings.DEFAULT_REGISTRATION_FEE)
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
}
