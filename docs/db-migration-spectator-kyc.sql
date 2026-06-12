ALTER TABLE spectator_profiles
    ADD COLUMN kyc_verification_id BIGINT NULL;

CREATE INDEX idx_spectator_profiles_kyc
    ON spectator_profiles (kyc_verification_id);

ALTER TABLE spectator_profiles
    ADD CONSTRAINT fk_spectator_profiles_kyc
    FOREIGN KEY (kyc_verification_id) REFERENCES kyc_verifications(id);
