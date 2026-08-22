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
            'LUNCH_GROUP_FORMED'
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
            'LUNCH_GROUP_FORMED'
        ));
