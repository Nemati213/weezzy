CREATE TABLE team_recommendation_sessions (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_request_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    page_size INTEGER NOT NULL CHECK (page_size BETWEEN 1 AND 50),
    entry_count INTEGER NOT NULL CHECK (entry_count >= 0),
    CONSTRAINT uq_team_recommendation_session_request
        UNIQUE (owner_user_id, client_request_id),
    CONSTRAINT team_recommendation_session_lifetime CHECK (expires_at > created_at)
);

CREATE INDEX idx_team_recommendation_sessions_owner_created
    ON team_recommendation_sessions (owner_user_id, created_at, id);
CREATE INDEX idx_team_recommendation_sessions_expiry
    ON team_recommendation_sessions (expires_at, id);

CREATE TABLE team_recommendation_entries (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES team_recommendation_sessions(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    first_delivered_at TIMESTAMP WITHOUT TIME ZONE,
    first_shown_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uq_team_recommendation_entry_ordinal UNIQUE (session_id, ordinal),
    CONSTRAINT uq_team_recommendation_entry_team UNIQUE (session_id, team_id),
    CONSTRAINT team_recommendation_entry_show_after_delivery
        CHECK (first_shown_at IS NULL OR
            (first_delivered_at IS NOT NULL AND first_shown_at >= first_delivered_at))
);

CREATE INDEX idx_team_recommendation_entries_team
    ON team_recommendation_entries (team_id) WHERE team_id IS NOT NULL;

CREATE TABLE team_recommendation_impressions (
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    last_shown_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (owner_user_id, team_id)
);

CREATE INDEX idx_team_recommendation_impressions_owner_shown
    ON team_recommendation_impressions (owner_user_id, last_shown_at, team_id);
CREATE INDEX idx_team_recommendation_impressions_retention
    ON team_recommendation_impressions (last_shown_at, owner_user_id, team_id);
CREATE INDEX idx_team_recommendation_impressions_team
    ON team_recommendation_impressions (team_id);

CREATE INDEX idx_team_applications_declined_lookup
    ON team_applications (applicant_profile_id, position_id, resolved_at DESC)
    WHERE status = 'DECLINED';
