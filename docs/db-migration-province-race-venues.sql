-- Add province and race venue settings for tournament/race location rules.
-- Safe to run before deploying code that writes tournaments.province_id and races.venue_id.

CREATE TABLE IF NOT EXISTS provinces (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    code VARCHAR(30) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_provinces_name UNIQUE (name),
    CONSTRAINT uk_provinces_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS race_venues (
    id BIGINT NOT NULL AUTO_INCREMENT,
    province_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    address VARCHAR(500) NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_race_venues_province FOREIGN KEY (province_id) REFERENCES provinces (id),
    CONSTRAINT uk_race_venues_province_name UNIQUE (province_id, name)
);

CREATE INDEX idx_race_venues_province ON race_venues (province_id);

ALTER TABLE tournaments
    ADD COLUMN province_id BIGINT NULL;

ALTER TABLE tournaments
    ADD CONSTRAINT fk_tournaments_province FOREIGN KEY (province_id) REFERENCES provinces (id);

ALTER TABLE races
    ADD COLUMN venue_id BIGINT NULL;

ALTER TABLE races
    ADD CONSTRAINT fk_races_venue FOREIGN KEY (venue_id) REFERENCES race_venues (id);

-- Optional backfill sketch for existing data:
-- 1. Insert provinces from known tournament.location values.
-- 2. Update tournaments.province_id to the matching province.
-- 3. Create venues per province.
-- 4. Update races.venue_id.
-- 5. After all old rows are populated, optionally make both columns NOT NULL.
