-- Move late check-in fee from tournaments to races.
-- Run after deploying code that can read races.late_check_in_fee.

ALTER TABLE races
ADD COLUMN late_check_in_fee DECIMAL(19,2) NOT NULL DEFAULT 0.00;

UPDATE races r
JOIN tournaments t ON r.tournament_id = t.id
SET r.late_check_in_fee = COALESCE(t.late_check_in_fee, 0.00);

-- Run only after the application is verified to no longer use tournaments.late_check_in_fee.
ALTER TABLE tournaments
DROP COLUMN late_check_in_fee;
