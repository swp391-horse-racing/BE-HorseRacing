ALTER TABLE jockey_invitations
    ADD COLUMN remuneration_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00;
