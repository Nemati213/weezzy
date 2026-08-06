CREATE TABLE auth_sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL,
    last_used_at timestamp NOT NULL,
    expires_at timestamp NOT NULL,
    revoked_at timestamp,
    revoke_reason varchar(30),
    user_agent varchar(512),
    ip_address varchar(45)
);

CREATE INDEX auth_sessions_user_id_idx
    ON auth_sessions(user_id);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    secret_hash varchar(64) NOT NULL,
    created_at timestamp NOT NULL,
    expires_at timestamp NOT NULL,
    used_at timestamp,
    revoked_at timestamp,
    replaced_by_token_id uuid REFERENCES refresh_tokens(id)
);

CREATE INDEX refresh_tokens_session_id_idx
    ON refresh_tokens(session_id);

CREATE UNIQUE INDEX refresh_tokens_one_active_per_session_idx
    ON refresh_tokens(session_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;
