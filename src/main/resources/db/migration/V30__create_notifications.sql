CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_user_id uuid NOT NULL
        REFERENCES users(id) ON DELETE CASCADE,
    type varchar(50) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    source_event_id uuid NOT NULL,
    read_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_notifications_recipient_event
        UNIQUE (recipient_user_id, source_event_id),
    CONSTRAINT notifications_type_check
        CHECK (type IN ('NEW_LIKE', 'NEW_MATCH', 'REPORT_DECISION', 'ADMIN_SANCTION'))
);

CREATE INDEX idx_notifications_recipient_cursor
    ON notifications (recipient_user_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_user_id, created_at DESC, id DESC)
    WHERE read_at IS NULL;
