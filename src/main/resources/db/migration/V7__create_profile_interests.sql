CREATE TABLE profile_interests (
    profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    interest_id uuid NOT NULL REFERENCES interests(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL,
    PRIMARY KEY (profile_id, interest_id)
);
