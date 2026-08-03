CREATE TABLE profile_interaction_events (
    id uuid PRIMARY KEY,
    source_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    target_profile_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    event_type varchar(40) NOT NULL,
    occurred_at timestamp NOT NULL,
    CHECK (source_profile_id <> target_profile_id),
    CHECK (event_type IN (
        'RECOMMENDATION_IMPRESSION',
        'LIKE',
        'PASS',
        'MATCH',
        'UNMATCH',
        'BLOCK',
        'UNBLOCK'
    ))
);

CREATE INDEX idx_profile_interaction_events_source_occurred
    ON profile_interaction_events (source_profile_id, occurred_at DESC, id DESC);

CREATE INDEX idx_profile_interaction_events_target_occurred
    ON profile_interaction_events (target_profile_id, occurred_at DESC, id DESC);

CREATE INDEX idx_profile_interaction_events_type_occurred
    ON profile_interaction_events (event_type, occurred_at DESC, id DESC);
