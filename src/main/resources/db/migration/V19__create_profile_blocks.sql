CREATE TABLE profile_blocks (
    blocker_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    blocked_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL,
    PRIMARY KEY (blocker_profile_id, blocked_profile_id),
    CHECK (blocker_profile_id <> blocked_profile_id)
);

CREATE INDEX idx_profile_blocks_blocked_blocker
    ON profile_blocks (blocked_profile_id, blocker_profile_id);
