package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.SystemEmailTemplatesSettingsRequest;
import com.minhthien.hoser_backend.dto.request.SystemFeesSettingsRequest;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
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
        request.setLateCheckInFee(new BigDecimal("500000"));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(settings)).thenReturn(settings);

        var response = service.updateFees(1L, request);

        assertEquals(new BigDecimal("5000000.00"), response.getDefaultRegistrationFee());
        assertEquals(new BigDecimal("500000.00"), response.getLateCheckInFee());
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
    void twoFactorPolicyMatchesConfiguredRoles() {
        SystemSettings settings = settings();
        when(settingsRepository.findById(SystemSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertTrue(service.requiresTwoFactor(UserRole.ADMIN));
        assertFalse(service.requiresTwoFactor(UserRole.OWNER));
    }

    private SystemSettings settings() {
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
                .build();
    }
}
