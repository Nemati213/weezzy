ALTER TABLE account_sanctions
    ADD COLUMN email_fingerprint VARCHAR(64);

CREATE INDEX account_sanction_email_fingerprints_idx
    ON account_sanctions(email_fingerprint)
    WHERE status = 'ACTIVE';
