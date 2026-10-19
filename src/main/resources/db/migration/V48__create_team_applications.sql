ALTER TABLE team_members ADD COLUMN position_id UUID
    REFERENCES team_request_positions(id) ON DELETE RESTRICT;
CREATE INDEX idx_team_members_position ON team_members (position_id)
    WHERE position_id IS NOT NULL;

CREATE TABLE team_applications (
    id UUID PRIMARY KEY,
    position_id UUID NOT NULL REFERENCES team_request_positions(id) ON DELETE CASCADE,
    applicant_profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
    message VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP WITHOUT TIME ZONE,
    CHECK ((status = 'PENDING' AND resolved_at IS NULL)
        OR (status <> 'PENDING' AND resolved_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_team_applications_pending
    ON team_applications (position_id, applicant_profile_id) WHERE status = 'PENDING';
CREATE INDEX idx_team_applications_applicant_created
    ON team_applications (applicant_profile_id, created_at DESC, id);
CREATE INDEX idx_team_applications_position_created
    ON team_applications (position_id, created_at DESC, id);

ALTER TABLE outbox_events DROP CONSTRAINT outbox_events_type_check;
ALTER TABLE outbox_events ADD CONSTRAINT outbox_events_type_check
    CHECK (event_type IN (
        'PROFILE_LIKED', 'MATCH_CREATED', 'REPORT_DECIDED',
        'ACCOUNT_SANCTION_CREATED', 'ACCOUNT_SANCTION_REVOKED',
        'LUNCH_GROUP_FORMED', 'LUNCH_EXTENSION_REQUESTED', 'LUNCH_GROUP_CANCELLED',
        'TEAM_INVITATION_SENT', 'TEAM_INVITATION_RESOLVED',
        'TEAM_APPLICATION_CHANGED'
    ));

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'NEW_LIKE', 'NEW_MATCH', 'REPORT_DECISION',
        'ADMIN_SANCTION', 'ADMIN_SANCTION_REVOKED',
        'LUNCH_GROUP_FORMED', 'LUNCH_EXTENSION_REQUESTED', 'LUNCH_GROUP_CANCELLED',
        'TEAM_INVITATION_RECEIVED', 'TEAM_INVITATION_ACCEPTED',
        'TEAM_INVITATION_DECLINED', 'TEAM_INVITATION_CANCELLED',
        'TEAM_APPLICATION_RECEIVED', 'TEAM_APPLICATION_ACCEPTED',
        'TEAM_APPLICATION_DECLINED', 'TEAM_APPLICATION_WITHDRAWN'
    ));
