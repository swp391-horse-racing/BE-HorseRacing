package com.minhthien.hoser_backend.repository;

import com.minhthien.hoser_backend.entity.FinanceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FinanceSettingsRepository extends JpaRepository<FinanceSettings, Long> {
    /**
     * Creates the singleton row atomically without overwriting existing settings.
     * The fixed primary key makes this safe across concurrent application instances.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO finance_settings (
                id,
                bet_winning_tax_percent,
                betting_enabled,
                created_at,
                updated_at,
                created_by,
                updated_by
            ) VALUES (1, 0.00, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 'system', 'system')
            """, nativeQuery = true)
    int insertDefaultIfAbsent();
}
