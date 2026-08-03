CREATE INDEX idx_profile_votes_source_created_target
    ON profile_votes (source_profile_id, created_at DESC, target_profile_id DESC);

CREATE INDEX idx_profile_blocks_blocker_created_blocked
    ON profile_blocks (blocker_profile_id, created_at DESC, blocked_profile_id DESC);

CREATE INDEX idx_profile_matches_first_created_second
    ON profile_matches (first_profile_id, created_at DESC, second_profile_id DESC);

CREATE INDEX idx_profile_matches_second_created_first
    ON profile_matches (second_profile_id, created_at DESC, first_profile_id DESC);

CREATE INDEX idx_profiles_created_id
    ON profiles (created_at DESC, id DESC);

CREATE INDEX idx_skills_name_id
    ON skills (name, id);

CREATE INDEX idx_interests_name_id
    ON interests (name, id);

CREATE INDEX idx_goals_name_id
    ON goals (name, id);
