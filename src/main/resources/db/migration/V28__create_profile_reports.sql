CREATE TABLE profile_reports (
    id uuid PRIMARY KEY,

    reporter_profile_id uuid NOT NULL
        REFERENCES profiles(id),

    target_profile_id uuid NOT NULL
        REFERENCES profiles(id),

    reason varchar(40) NOT NULL,
    comment varchar(1000),

    status varchar(20) NOT NULL DEFAULT 'PENDING',

    decision varchar(1000),
    reviewed_by_user_id uuid
                             REFERENCES users(id) ON DELETE SET NULL,

    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    reviewed_at timestamp,
    closed_at timestamp,

    CONSTRAINT profile_reports_not_self_check
        CHECK (reporter_profile_id <> target_profile_id)
);

CREATE UNIQUE INDEX profile_reports_open_pair_idx
    ON profile_reports(reporter_profile_id, target_profile_id)
    WHERE status IN ('PENDING', 'REVIEWED');

CREATE INDEX profile_reports_queue_idx
    ON profile_reports(status, created_at, id);

CREATE INDEX profile_reports_target_idx
    ON profile_reports(target_profile_id, created_at DESC);
