CREATE TABLE profile_goals (
    profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    goal_id uuid NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    created_at timestamp NOT NULL,
    PRIMARY KEY (profile_id, goal_id)
);
