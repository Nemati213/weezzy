CREATE TABLE profile_skills (
    profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    skill_id uuid NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL,
    PRIMARY KEY (profile_id, skill_id)
);
