-- Idempotent MySQL migration for betting-tax snapshots and removal of configurable
-- owner/jockey race-prize sharing.

-- Change only the untouched system-created 0% setting. An explicit admin value,
-- including an intentional 0%, remains unchanged.
UPDATE finance_settings
SET bet_winning_tax_percent = 10.00,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE id = 1
  AND bet_winning_tax_percent = 0.00
  AND created_by = 'system'
  AND updated_by = 'system'
  AND created_at = updated_at;

ALTER TABLE finance_settings
    ALTER COLUMN bet_winning_tax_percent SET DEFAULT 10.00;

SET @add_market_tax_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE bet_markets ADD COLUMN winning_tax_percent DECIMAL(5,2) NULL AFTER max_stake',
        'SELECT 1'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'bet_markets'
      AND column_name = 'winning_tax_percent'
);
PREPARE add_market_tax_statement FROM @add_market_tax_sql;
EXECUTE add_market_tax_statement;
DEALLOCATE PREPARE add_market_tax_statement;

UPDATE bet_markets
SET winning_tax_percent = COALESCE(
    (SELECT bet_winning_tax_percent FROM finance_settings WHERE id = 1),
    10.00
)
WHERE winning_tax_percent IS NULL;

ALTER TABLE bet_markets
    MODIFY COLUMN winning_tax_percent DECIMAL(5,2) NOT NULL DEFAULT 10.00;

-- Existing active bets inherit their market snapshot. Settled rows that already
-- contain a tax snapshot are never overwritten.
UPDATE bets b
JOIN bet_markets bm ON bm.id = b.market_id
SET b.winning_tax_percent = bm.winning_tax_percent
WHERE b.winning_tax_percent IS NULL
  AND b.status IN ('PLACED', 'LOCKED', 'UNPAID');

DROP TABLE IF EXISTS race_prize_share_settings;
