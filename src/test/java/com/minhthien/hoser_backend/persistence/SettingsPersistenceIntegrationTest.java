package com.minhthien.hoser_backend.persistence;

import com.minhthien.hoser_backend.entity.FinanceSettings;
import com.minhthien.hoser_backend.entity.SystemSettings;
import com.minhthien.hoser_backend.repository.FinanceSettingsRepository;
import com.minhthien.hoser_backend.repository.SystemSettingsRepository;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.impl.FinanceSettingsServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settings;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.test.database.replace=NONE"
})
@Import({FinanceSettingsServiceImpl.class, SettingsPersistenceIntegrationTest.CacheTestConfiguration.class})
class SettingsPersistenceIntegrationTest {

    @Autowired private FinanceSettingsService financeSettingsService;
    @Autowired private FinanceSettingsRepository financeSettingsRepository;
    @Autowired private SystemSettingsRepository systemSettingsRepository;
    @Autowired private EntityManager entityManager;

    @TestConfiguration
    static class CacheTestConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Test
    void emptyFinanceSettingsTableCreatesOneEnabledRowAndRepeatedInitializationDoesNotDuplicate() {
        financeSettingsRepository.deleteAllInBatch();

        FinanceSettings firstStartup = financeSettingsService.getOrCreateSettings();
        FinanceSettings nextStartup = financeSettingsService.getOrCreateSettings();

        assertEquals(FinanceSettings.SINGLETON_ID, firstStartup.getId());
        assertEquals(firstStartup.getId(), nextStartup.getId());
        assertEquals(1L, financeSettingsRepository.count());
        assertEquals(FinanceSettings.DEFAULT_BET_WINNING_TAX_PERCENT,
                firstStartup.getBetWinningTaxPercent());
        assertTrue(firstStartup.getBettingEnabled());
        assertEquals("system", firstStartup.getCreatedBy());
        assertEquals("system", firstStartup.getUpdatedBy());
    }

    @Test
    void existingDisabledFinanceSettingsRemainsDisabled() {
        financeSettingsRepository.deleteAllInBatch();
        FinanceSettings settings = financeSettingsService.getOrCreateSettings();
        settings.setBettingEnabled(false);
        financeSettingsRepository.saveAndFlush(settings);
        entityManager.clear();

        assertFalse(financeSettingsService.isBettingEnabled());
        assertEquals(1L, financeSettingsRepository.count());
    }

    @Test
    void jsonSettingsLongerThan255CharactersPersistWithoutTruncation() {
        String longDistancesJson = "[" + "1000,".repeat(80) + "1200]";
        String longPenaltyRulesJson = "[{\"rule\":\"" + "p".repeat(400) + "\"}]";
        String longViolationTypesJson = "[{\"label\":\"" + "v".repeat(400) + "\"}]";
        SystemSettings settings = SystemSettings.builder()
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
                .raceDistancesMetersJson(longDistancesJson)
                .violationPenaltyRulesJson(longPenaltyRulesJson)
                .violationTypeOptionsJson(longViolationTypesJson)
                .build();

        systemSettingsRepository.saveAndFlush(settings);
        entityManager.clear();
        SystemSettings reloaded = systemSettingsRepository.findById(SystemSettings.SINGLETON_ID).orElseThrow();

        assertTrue(reloaded.getRaceDistancesMetersJson().length() > 255);
        assertEquals(longDistancesJson, reloaded.getRaceDistancesMetersJson());
        assertEquals(longPenaltyRulesJson, reloaded.getViolationPenaltyRulesJson());
        assertEquals(longViolationTypesJson, reloaded.getViolationTypeOptionsJson());
    }
}
