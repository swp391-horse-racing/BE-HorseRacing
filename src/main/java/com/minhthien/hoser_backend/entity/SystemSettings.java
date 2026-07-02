package com.minhthien.hoser_backend.entity;

import com.minhthien.hoser_backend.enums.TwoFactorPolicy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettings {
    public static final Long SINGLETON_ID = 1L;
    public static final BigDecimal DEFAULT_REGISTRATION_FEE = new BigDecimal("5000000");
    public static final BigDecimal DEFAULT_LATE_CHECK_IN_FEE = new BigDecimal("500000");
    public static final String DEFAULT_RULES = """
            1. Ngua phai co giay chung nhan suc khoe hop le.
            2. Jockey phai co chung chi FIA.
            3. Kiem tra doping bat buoc.""";
    public static final String DEFAULT_REGISTRATION_OPEN_SUBJECT =
            "[HorseRacing] Mo dang ky giai dau {{tournament}}";
    public static final String DEFAULT_CHECK_IN_REMINDER_SUBJECT =
            "[HorseRacing] Nhac check-in cuoc dua {{race}}";
    public static final String DEFAULT_RACE_RESULT_SUBJECT =
            "[HorseRacing] Ket qua cuoc dua {{race}}";
    public static final TwoFactorPolicy DEFAULT_TWO_FACTOR_POLICY = TwoFactorPolicy.DISABLED;
    public static final int DEFAULT_SESSION_DURATION_MINUTES = 60;
    public static final String DEFAULT_SYSTEM_NAME = "Horse Racing Admin";
    public static final String DEFAULT_PRIMARY_COLOR = "#D4A017";
    public static final String DEFAULT_RACE_DISTANCES_METERS_JSON = "[1000,1200,1500]";
    public static final String DEFAULT_VIOLATION_PENALTY_RULES_JSON = """
            [
              {"severity":"WARNING","resultAction":"NONE","timePenaltyMillis":0},
              {"severity":"MINOR","resultAction":"TIME_PENALTY","timePenaltyMillis":3000},
              {"severity":"MAJOR","resultAction":"TIME_PENALTY","timePenaltyMillis":10000},
              {"severity":"DISQUALIFICATION","resultAction":"DISQUALIFY","timePenaltyMillis":0}
            ]""";
    public static final String DEFAULT_VIOLATION_TYPE_OPTIONS_JSON = """
            [
              {"code":"FALSE_START","label":"Xuất phát sai","active":true},
              {"code":"DANGEROUS_RIDING","label":"Lái nguy hiểm","active":true},
              {"code":"EQUIPMENT_VIOLATION","label":"Vi phạm trang bị","active":true},
              {"code":"DOPING_SUSPECTED","label":"Nghi doping","active":true},
              {"code":"LATE_CHECK_IN","label":"Check-in muộn","active":true},
              {"code":"OTHER","label":"Khác","active":true}
            ]""";

    @Id
    private Long id;

    @Column(name = "default_registration_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal defaultRegistrationFee;

    @Column(name = "late_check_in_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal lateCheckInFee;

    @Lob
    @Column(name = "default_tournament_rules", nullable = false)
    private String defaultTournamentRules;

    @Column(name = "registration_open_email_subject", nullable = false, length = 300)
    private String registrationOpenEmailSubject;

    @Column(name = "check_in_reminder_email_subject", nullable = false, length = 300)
    private String checkInReminderEmailSubject;

    @Column(name = "race_result_email_subject", nullable = false, length = 300)
    private String raceResultEmailSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_policy", nullable = false, length = 30)
    private TwoFactorPolicy twoFactorPolicy;

    @Column(name = "session_duration_minutes", nullable = false)
    private Integer sessionDurationMinutes;

    @Column(name = "system_name", nullable = false, length = 100)
    private String systemName;

    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;

    @Lob
    @Column(name = "race_distances_meters_json", nullable = false)
    private String raceDistancesMetersJson;

    @Lob
    @Column(name = "violation_penalty_rules_json", nullable = false)
    private String violationPenaltyRulesJson;

    @Lob
    @Column(name = "violation_type_options_json", nullable = false)
    private String violationTypeOptionsJson;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false, length = 100)
    @Builder.Default
    private String updatedBy = "SYSTEM";

    @PrePersist
    void onCreate() {
        if (id == null) id = SINGLETON_ID;
        applyDefaults();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (updatedBy == null || updatedBy.isBlank()) updatedBy = "SYSTEM";
    }

    @PreUpdate
    void onUpdate() {
        applyDefaults();
        updatedAt = LocalDateTime.now();
        if (updatedBy == null || updatedBy.isBlank()) updatedBy = "SYSTEM";
    }

    private void applyDefaults() {
        if (defaultRegistrationFee == null) defaultRegistrationFee = DEFAULT_REGISTRATION_FEE;
        if (lateCheckInFee == null) lateCheckInFee = DEFAULT_LATE_CHECK_IN_FEE;
        if (defaultTournamentRules == null) defaultTournamentRules = DEFAULT_RULES;
        if (registrationOpenEmailSubject == null) registrationOpenEmailSubject = DEFAULT_REGISTRATION_OPEN_SUBJECT;
        if (checkInReminderEmailSubject == null) checkInReminderEmailSubject = DEFAULT_CHECK_IN_REMINDER_SUBJECT;
        if (raceResultEmailSubject == null) raceResultEmailSubject = DEFAULT_RACE_RESULT_SUBJECT;
        if (twoFactorPolicy == null) twoFactorPolicy = DEFAULT_TWO_FACTOR_POLICY;
        if (sessionDurationMinutes == null) sessionDurationMinutes = DEFAULT_SESSION_DURATION_MINUTES;
        if (systemName == null) systemName = DEFAULT_SYSTEM_NAME;
        if (primaryColor == null) primaryColor = DEFAULT_PRIMARY_COLOR;
        if (raceDistancesMetersJson == null || raceDistancesMetersJson.isBlank()) {
            raceDistancesMetersJson = DEFAULT_RACE_DISTANCES_METERS_JSON;
        }
        if (violationPenaltyRulesJson == null || violationPenaltyRulesJson.isBlank()) {
            violationPenaltyRulesJson = DEFAULT_VIOLATION_PENALTY_RULES_JSON;
        }
        if (violationTypeOptionsJson == null || violationTypeOptionsJson.isBlank()) {
            violationTypeOptionsJson = DEFAULT_VIOLATION_TYPE_OPTIONS_JSON;
        }
    }
}
