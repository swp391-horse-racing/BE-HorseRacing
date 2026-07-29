-- MySQL production migration for referee race simulation and review drafts.

CREATE TABLE race_simulations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    race_id BIGINT NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    seed VARCHAR(128) NOT NULL,
    algorithm_version VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    playback_duration_ms BIGINT NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    playback_ends_at DATETIME(6) NOT NULL,
    generated_by BIGINT NOT NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by BIGINT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_race_simulations_race UNIQUE (race_id),
    CONSTRAINT uk_race_simulations_run UNIQUE (run_id),
    CONSTRAINT fk_race_simulations_race FOREIGN KEY (race_id) REFERENCES races(id)
);

CREATE TABLE race_simulation_participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    simulation_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    horse_id BIGINT NOT NULL,
    horse_name VARCHAR(120) NOT NULL,
    jockey_id BIGINT NOT NULL,
    jockey_name VARCHAR(120) NOT NULL,
    gate_number INT NOT NULL,
    horse_starts BIGINT NULL,
    horse_wins BIGINT NULL,
    horse_win_rate DOUBLE NULL,
    jockey_starts BIGINT NULL,
    jockey_wins BIGINT NULL,
    jockey_win_rate DOUBLE NULL,
    history_score DOUBLE NULL,
    result_rank INT NOT NULL,
    finish_time_millis BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_simulation_participant UNIQUE (simulation_id, participant_id),
    CONSTRAINT uk_simulation_rank UNIQUE (simulation_id, result_rank),
    CONSTRAINT fk_simulation_participant_simulation
        FOREIGN KEY (simulation_id) REFERENCES race_simulations(id),
    CONSTRAINT fk_simulation_participant_participant
        FOREIGN KEY (participant_id) REFERENCES race_participants(id)
);

CREATE TABLE race_simulation_checkpoints (
    id BIGINT NOT NULL AUTO_INCREMENT,
    simulation_participant_id BIGINT NOT NULL,
    tick INT NOT NULL,
    at_ratio DOUBLE NOT NULL,
    progress DOUBLE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_simulation_checkpoint_tick UNIQUE (simulation_participant_id, tick),
    CONSTRAINT fk_checkpoint_simulation_participant
        FOREIGN KEY (simulation_participant_id) REFERENCES race_simulation_participants(id)
);

CREATE TABLE race_result_drafts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    race_id BIGINT NOT NULL,
    simulation_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    draft_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    published_at DATETIME(6) NULL,
    published_by BIGINT NULL,
    entity_version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_race_result_drafts_race UNIQUE (race_id),
    CONSTRAINT uk_race_result_drafts_simulation UNIQUE (simulation_id),
    CONSTRAINT fk_race_result_drafts_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_race_result_drafts_simulation FOREIGN KEY (simulation_id) REFERENCES race_simulations(id)
);

CREATE TABLE race_result_draft_rows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    base_rank INT NOT NULL,
    result_rank INT NULL,
    base_finish_time_millis BIGINT NOT NULL,
    penalty_time_millis BIGINT NOT NULL DEFAULT 0,
    finish_time_millis BIGINT NULL,
    status VARCHAR(30) NOT NULL,
    disqualification_reason VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_result_draft_participant UNIQUE (draft_id, participant_id),
    CONSTRAINT fk_result_draft_rows_draft FOREIGN KEY (draft_id) REFERENCES race_result_drafts(id),
    CONSTRAINT fk_result_draft_rows_participant FOREIGN KEY (participant_id) REFERENCES race_participants(id)
);

ALTER TABLE race_results
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN simulation_run_id VARCHAR(64) NULL,
    ADD COLUMN base_finish_time_millis BIGINT NULL,
    ADD COLUMN penalty_time_millis BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_race_results_simulation_run ON race_results(simulation_run_id);
