CREATE TABLE email_verification_tokens (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    secret_hash varchar(64) NOT NULL,
    created_at timestamp NOT NULL,
    expires_at timestamp NOT NULL,
    used_at timestamp,
    revoked_at timestamp
);

CREATE INDEX idx_evt_user_id
    ON email_verification_tokens (user_id);

CREATE UNIQUE INDEX idx_evt_one_active_per_user
    ON email_verification_tokens (user_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;