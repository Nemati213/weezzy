CREATE TABLE team_requests (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL CHECK (LENGTH(TRIM(title)) > 0),
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CLOSED')),
    recruitment_deadline TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    target_team_size INTEGER NOT NULL CHECK (target_team_size > 0),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_team_requests_team_created_at
    ON team_requests (team_id, created_at DESC, id);
CREATE INDEX idx_team_requests_team_status_created_at
    ON team_requests (team_id, status, created_at DESC, id);
CREATE INDEX idx_team_requests_status_deadline
    ON team_requests (status, recruitment_deadline, id);

CREATE TABLE team_request_positions (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES team_requests(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    description VARCHAR(2000),
    slots_count INTEGER NOT NULL CHECK (slots_count > 0),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_team_request_positions_request_created_at
    ON team_request_positions (request_id, created_at, id);

CREATE TABLE team_request_position_skills (
    position_id UUID NOT NULL REFERENCES team_request_positions(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    PRIMARY KEY (position_id, skill_id)
);

CREATE INDEX idx_team_request_position_skills_skill_id
    ON team_request_position_skills (skill_id, position_id);
