CREATE TABLE profile_votes (
    source_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    target_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    action varchar(20) NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    PRIMARY KEY (source_profile_id, target_profile_id),
    CHECK (source_profile_id <> target_profile_id),
    CHECK (action IN ('LIKE', 'PASS'))
);