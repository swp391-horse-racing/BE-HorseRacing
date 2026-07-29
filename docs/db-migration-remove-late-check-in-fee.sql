-- Remove the retired late check-in fee feature after deploying the application update.
-- Back up the database before running this migration.

ALTER TABLE race_participants
    DROP COLUMN late_check_in_fee_debit_key,
    DROP COLUMN late_check_in_fee_amount;

ALTER TABLE races
    DROP COLUMN late_check_in_fee;

ALTER TABLE system_settings
    DROP COLUMN late_check_in_fee;
