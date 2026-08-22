ALTER TABLE lunch_groups
    ADD COLUMN cancellation_reason VARCHAR(50),
    ADD COLUMN lifecycle_checked_at TIMESTAMP WITHOUT TIME ZONE,
    ADD CONSTRAINT lunch_groups_cancellation_reason_check
        CHECK (cancellation_reason IN (
            'INSUFFICIENT_MEMBERS',
            'MEMBER_INELIGIBLE',
            'MEMBERS_INCOMPATIBLE'
        ));

CREATE INDEX idx_lunch_groups_lifecycle_validation
ON lunch_groups (lifecycle_checked_at NULLS FIRST, time_slot, id)
WHERE status = 'ACTIVE';

ALTER TABLE lunch_group_members
    ADD COLUMN released_at TIMESTAMP WITHOUT TIME ZONE,
    DROP CONSTRAINT lunch_group_members_lunch_request_id_key;

CREATE UNIQUE INDEX idx_lunch_group_members_current_request
ON lunch_group_members (lunch_request_id)
WHERE released_at IS NULL;

ALTER TABLE outbox_events
    DROP CONSTRAINT outbox_events_type_check;

ALTER TABLE outbox_events
    ADD CONSTRAINT outbox_events_type_check
        CHECK (event_type IN (
            'PROFILE_LIKED',
            'MATCH_CREATED',
            'REPORT_DECIDED',
            'ACCOUNT_SANCTION_CREATED',
            'ACCOUNT_SANCTION_REVOKED',
            'LUNCH_GROUP_FORMED',
            'LUNCH_EXTENSION_REQUESTED',
            'LUNCH_GROUP_CANCELLED'
        ));

ALTER TABLE notifications
    DROP CONSTRAINT notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
        CHECK (type IN (
            'NEW_LIKE',
            'NEW_MATCH',
            'REPORT_DECISION',
            'ADMIN_SANCTION',
            'ADMIN_SANCTION_REVOKED',
            'LUNCH_GROUP_FORMED',
            'LUNCH_EXTENSION_REQUESTED',
            'LUNCH_GROUP_CANCELLED'
        ));
