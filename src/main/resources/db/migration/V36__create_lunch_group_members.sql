CREATE TABLE lunch_group_members (
    group_id UUID NOT NULL REFERENCES lunch_groups(id) ON DELETE CASCADE,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    lunch_request_id UUID NOT NULL REFERENCES lunch_requests(id) ON DELETE RESTRICT,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),

    PRIMARY KEY (group_id, profile_id),
    UNIQUE (lunch_request_id)
);

CREATE INDEX idx_lunch_group_members_profile_id
ON lunch_group_members (profile_id);
