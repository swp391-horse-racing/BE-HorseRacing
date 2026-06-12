ALTER TABLE tournaments
    ADD COLUMN min_horses_per_owner INT NOT NULL DEFAULT 4,
    ADD COLUMN max_horses_per_owner INT NOT NULL DEFAULT 10;

UPDATE tournaments
SET min_horses_per_owner = 4
WHERE min_horses_per_owner IS NULL OR min_horses_per_owner < 4;

UPDATE tournaments
SET max_horses_per_owner = min_horses_per_owner + 6
WHERE max_horses_per_owner IS NULL
   OR max_horses_per_owner < min_horses_per_owner + 6;
