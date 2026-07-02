package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.request.*;
import com.minhthien.hoser_backend.dto.response.PublicBrandingResponse;
import com.minhthien.hoser_backend.dto.response.RaceDistanceOptionResponse;
import com.minhthien.hoser_backend.dto.response.SystemSettingsResponse;
import com.minhthien.hoser_backend.dto.response.ViolationPenaltyRuleResponse;
import com.minhthien.hoser_backend.dto.response.ViolationTypeOptionResponse;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.enums.UserRole;

import java.util.List;

public interface SystemSettingsService {
    SystemSettingsResponse getSettings();

    PublicBrandingResponse getPublicBranding();

    SystemSettingsResponse updateFees(Long adminId, SystemFeesSettingsRequest request);

    SystemSettingsResponse updateRules(Long adminId, SystemRulesSettingsRequest request);

    SystemSettingsResponse updateEmailTemplates(Long adminId, SystemEmailTemplatesSettingsRequest request);

    SystemSettingsResponse updateSecurity(Long adminId, SystemSecuritySettingsRequest request);

    SystemSettingsResponse updateBranding(Long adminId, SystemBrandingSettingsRequest request);

    SystemSettingsResponse updateRaceDistances(Long adminId, SystemRaceDistancesSettingsRequest request);

    SystemSettingsResponse updateViolationRules(Long adminId, SystemViolationRulesSettingsRequest request);

    SystemSettingsResponse updateViolationTypes(Long adminId, SystemViolationTypesSettingsRequest request);

    List<ViolationPenaltyRuleResponse> getViolationPenaltyRules();

    List<ViolationTypeOptionResponse> getViolationTypes();

    SystemSettings getCurrent();

    List<RaceDistanceOptionResponse> getRaceDistanceOptions();

    String normalizeRaceDistance(String distance);

    ViolationTypeOptionResponse requireActiveViolationType(String typeCode);

    boolean requiresTwoFactor(UserRole role);

    String renderSubject(String template, String tournamentName, String raceName);
}
