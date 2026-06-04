ALTER TABLE jockey_profiles
    DROP COLUMN hire_price;

ALTER TABLE jockey_invitations
    DROP COLUMN hire_price,
    DROP COLUMN tax_percent,
    DROP COLUMN tax_amount,
    DROP COLUMN jockey_payout_amount,
    DROP COLUMN funds_held_at,
    DROP COLUMN paid_at;

ALTER TABLE finance_settings
    DROP COLUMN jockey_hire_tax_percent;
