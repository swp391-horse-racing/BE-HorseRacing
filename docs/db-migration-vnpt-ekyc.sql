-- Run once before deploying the VNPT eKYC backend when Hibernate DDL auto-update
-- is disabled in the target environment. Existing legacy KYC rows remain intact.
ALTER TABLE kyc_verifications
    ADD COLUMN vnpt_front_image_hash VARCHAR(255) NULL;
