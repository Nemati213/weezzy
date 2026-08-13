CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    event_type varchar(50) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at timestamp,
    locked_by varchar(100),
    last_error varchar(2000),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at timestamp,

    CONSTRAINT outbox_events_type_check
        CHECK (event_type IN (
            'PROFILE_LIKED',
            'MATCH_CREATED',
            'REPORT_DECIDED',
            'ACCOUNT_SANCTION_CREATED'
        )),
    CONSTRAINT outbox_events_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT outbox_events_attempt_count_check
        CHECK (attempt_count >= 0),
    CONSTRAINT outbox_events_lock_check
        CHECK (
            (status = 'PROCESSING' AND locked_at IS NOT NULL AND locked_by IS NOT NULL)
            OR
            (status <> 'PROCESSING' AND locked_at IS NULL AND locked_by IS NULL)
        ),
    CONSTRAINT outbox_events_processed_check
        CHECK (
            (status = 'PROCESSED' AND processed_at IS NOT NULL)
            OR
            (status <> 'PROCESSED' AND processed_at IS NULL)
        )
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_events_processing
    ON outbox_events (locked_at)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_outbox_events_failed
    ON outbox_events (updated_at, id)
    WHERE status = 'FAILED';

CREATE INDEX idx_outbox_events_processed_cleanup
    ON outbox_events (processed_at, id)
    WHERE status = 'PROCESSED';
