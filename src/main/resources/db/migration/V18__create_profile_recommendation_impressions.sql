CREATE TABLE profile_recommendation_impressions (
    source_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    target_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    shown_at timestamp NOT NULL,
    PRIMARY KEY (source_profile_id, target_profile_id),
    CHECK (source_profile_id <> target_profile_id)
);

CREATE INDEX idx_profile_recommendation_impressions_source_shown_at
    ON profile_recommendation_impressions (source_profile_id, shown_at);
