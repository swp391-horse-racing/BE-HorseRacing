package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.*;
import com.minhthien.hoser_backend.dto.response.PublicBrandingResponse;
import com.minhthien.hoser_backend.dto.response.SystemSettingsResponse;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.enums.UserRole;

public interface SystemSettingsService {
    SystemSettingsResponse getSettings();

    PublicBrandingResponse getPublicBranding();

    SystemSettingsResponse updateFees(Long adminId, SystemFeesSettingsRequest request);

    SystemSettingsResponse updateRules(Long adminId, SystemRulesSettingsRequest request);

    SystemSettingsResponse updateEmailTemplates(Long adminId, SystemEmailTemplatesSettingsRequest request);

    SystemSettingsResponse updateSecurity(Long adminId, SystemSecuritySettingsRequest request);

    SystemSettingsResponse updateBranding(Long adminId, SystemBrandingSettingsRequest request);

    SystemSettings getCurrent();

    boolean requiresTwoFactor(UserRole role);

    String renderSubject(String template, String tournamentName, String raceName);
}
