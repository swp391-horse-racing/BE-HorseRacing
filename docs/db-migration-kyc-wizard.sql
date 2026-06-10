CREATE TABLE IF NOT EXISTS kyc_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    requested_role VARCHAR(30) NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'FPT_AI',
    status VARCHAR(30) NOT NULL,
    id_number_hash VARCHAR(64) NULL,
    id_number_masked VARCHAR(50) NULL,
    full_name VARCHAR(255) NULL,
    date_of_birth VARCHAR(50) NULL,
    gender VARCHAR(50) NULL,
    address TEXT NULL,
    issue_date VARCHAR(50) NULL,
    front_ocr_passed BOOLEAN NOT NULL DEFAULT FALSE,
    face_matched BOOLEAN NOT NULL DEFAULT FALSE,
    face_score DECIMAL(8,4) NULL,
    front_image_url TEXT NULL,
    back_image_url TEXT NULL,
    selfie_image_url TEXT NULL,
    raw_front_response LONGTEXT NULL,
    raw_face_response LONGTEXT NULL,
    reject_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_kyc_verifications_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_kyc_user (user_id),
    INDEX idx_kyc_status (status),
    INDEX idx_kyc_id_hash (id_number_hash)
);

ALTER TABLE owner_profiles
    ADD COLUMN kyc_verification_id BIGINT NULL,
    ADD CONSTRAINT uk_owner_profiles_kyc UNIQUE (kyc_verification_id),
    ADD CONSTRAINT fk_owner_profiles_kyc
        FOREIGN KEY (kyc_verification_id) REFERENCES kyc_verifications (id);

ALTER TABLE jockey_profiles
    ADD COLUMN kyc_verification_id BIGINT NULL,
    ADD CONSTRAINT uk_jockey_profiles_kyc UNIQUE (kyc_verification_id),
    ADD CONSTRAINT fk_jockey_profiles_kyc
        FOREIGN KEY (kyc_verification_id) REFERENCES kyc_verifications (id);

ALTER TABLE referee_profiles
    ADD COLUMN kyc_verification_id BIGINT NULL,
    ADD CONSTRAINT uk_referee_profiles_kyc UNIQUE (kyc_verification_id),
    ADD CONSTRAINT fk_referee_profiles_kyc
        FOREIGN KEY (kyc_verification_id) REFERENCES kyc_verifications (id);

-- Existing applications remain valid. New OWNER/JOCKEY/REFEREE submissions
-- are written as DRAFT by the application and become PENDING only after KYC.
