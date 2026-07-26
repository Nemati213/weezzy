CREATE INDEX profiles_status_idx
ON profiles (status);

CREATE INDEX profile_votes_target_source_idx
ON profile_votes (target_profile_id, source_profile_id);

CREATE INDEX profile_matches_second_profile_idx
ON profile_matches (second_profile_id);

CREATE INDEX profile_skills_skill_profile_idx
ON profile_skills (skill_id, profile_id);

CREATE INDEX profile_interests_interest_profile_idx
ON profile_interests (interest_id, profile_id);

CREATE INDEX profile_goals_goal_profile_idx
ON profile_goals (goal_id, profile_id);
