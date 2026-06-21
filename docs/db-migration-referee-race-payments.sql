-- Add named referee salary configurations and per-race payment reservation ledger.

CREATE TABLE referee_salary_configs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    race_type VARCHAR(100) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    active BIT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_referee_salary_configs_name UNIQUE (name),
    INDEX idx_referee_salary_configs_active (active),
    INDEX idx_referee_salary_configs_race_type (race_type)
);

INSERT INTO referee_salary_configs
    (name, race_type, amount, active, created_at, updated_at, created_by, updated_by)
VALUES
    ('Default referee salary', 'ALL', 500000.00, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 0);

CREATE TABLE referee_race_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    race_id BIGINT NOT NULL,
    referee_id BIGINT NOT NULL,
    salary_config_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    hold_idempotency_key VARCHAR(150) NOT NULL,
    capture_idempotency_key VARCHAR(150) NOT NULL,
    credit_idempotency_key VARCHAR(150) NOT NULL,
    held_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    released_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_referee_race_payments_race UNIQUE (race_id),
    CONSTRAINT fk_referee_race_payments_race
        FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT fk_referee_race_payments_referee
        FOREIGN KEY (referee_id) REFERENCES users(id),
    CONSTRAINT fk_referee_race_payments_salary_config
        FOREIGN KEY (salary_config_id) REFERENCES referee_salary_configs(id),
    INDEX idx_referee_race_payments_referee (referee_id),
    INDEX idx_referee_race_payments_status (status)
);
