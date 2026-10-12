CREATE TABLE teams (
    id UUID PRIMARY KEY,
    owner_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_teams_owner_profile_id ON teams (owner_profile_id);
CREATE INDEX idx_teams_status_created_at ON teams (status, created_at DESC, id);

CREATE TABLE team_members (
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, profile_id)
);

CREATE INDEX idx_team_members_profile_id ON team_members (profile_id);

CREATE TABLE team_invitations (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    inviter_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    invitee_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT team_invitations_not_self_check
        CHECK (inviter_profile_id <> invitee_profile_id),
    CONSTRAINT team_invitations_resolution_check
        CHECK (
            (status = 'PENDING' AND resolved_at IS NULL)
            OR (status <> 'PENDING' AND resolved_at IS NOT NULL)
        ),
    CONSTRAINT team_invitations_resolution_time_check
        CHECK (resolved_at IS NULL OR resolved_at >= created_at)
);

CREATE UNIQUE INDEX idx_team_invitations_pending_pair
    ON team_invitations (team_id, invitee_profile_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_team_invitations_team_created_at
    ON team_invitations (team_id, created_at DESC, id);

CREATE INDEX idx_team_invitations_invitee_status_created_at
    ON team_invitations (invitee_profile_id, status, created_at DESC, id);

CREATE INDEX idx_team_invitations_inviter_profile_id
    ON team_invitations (inviter_profile_id);
