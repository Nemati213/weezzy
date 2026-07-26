CREATE TABLE profile_matches (
    first_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    second_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL,
    PRIMARY KEY (first_profile_id, second_profile_id),
    CHECK (first_profile_id <> second_profile_id),
    CHECK (first_profile_id < second_profile_id)
);
