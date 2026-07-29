-- Idempotent MySQL migration for singleton finance settings and LONGTEXT JSON settings.
-- Existing finance configuration is never overwritten.

INSERT INTO finance_settings (
    id,
    bet_winning_tax_percent,
    betting_enabled,
    created_at,
    updated_at,
    created_by,
    updated_by
) VALUES (
    1,
    10.00,
    TRUE,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6),
    'system',
    'system'
) ON DUPLICATE KEY UPDATE id = id;

ALTER TABLE system_settings
    MODIFY COLUMN race_distances_meters_json LONGTEXT NOT NULL,
    MODIFY COLUMN violation_penalty_rules_json LONGTEXT NULL,
    MODIFY COLUMN violation_type_options_json LONGTEXT NULL;
