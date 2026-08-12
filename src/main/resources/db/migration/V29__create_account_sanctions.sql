CREATE TABLE account_sanctions (
    id uuid PRIMARY KEY,
    target_user_id uuid NOT NULL,
    target_profile_id uuid REFERENCES profiles(id),
    source_report_id uuid REFERENCES profile_reports(id) ON DELETE SET NULL,
    type varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    reason varchar(1000) NOT NULL,
    expires_at timestamp,
    created_by_user_id uuid NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    revoked_at timestamp,
    revoked_by_user_id uuid,
    revocation_reason varchar(1000),

    CONSTRAINT account_sanctions_type_check
        CHECK (type IN ('TEMPORARY_SUSPENSION', 'PERMANENT_BAN')),
    CONSTRAINT account_sanctions_status_check
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    CONSTRAINT account_sanctions_reason_not_blank_check
        CHECK (length(btrim(reason)) > 0),
    CONSTRAINT account_sanctions_expiration_check
        CHECK (
            (type = 'TEMPORARY_SUSPENSION' AND expires_at IS NOT NULL)
            OR (type = 'PERMANENT_BAN' AND expires_at IS NULL)
        ),
    CONSTRAINT account_sanctions_revocation_check
        CHECK (
            (status = 'REVOKED'
                AND revoked_at IS NOT NULL
                AND revoked_by_user_id IS NOT NULL
                AND revocation_reason IS NOT NULL
                AND length(btrim(revocation_reason)) > 0)
            OR (status <> 'REVOKED'
                AND revoked_at IS NULL
                AND revoked_by_user_id IS NULL
                AND revocation_reason IS NULL)
        )
);

CREATE UNIQUE INDEX account_sanctions_one_active_per_user_idx
    ON account_sanctions(target_user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX account_sanctions_status_queue_idx
    ON account_sanctions(status, created_at DESC, id DESC);

CREATE INDEX account_sanctions_target_history_idx
    ON account_sanctions(target_user_id, created_at DESC, id DESC);

CREATE INDEX account_sanctions_target_profile_idx
    ON account_sanctions(target_profile_id, created_at DESC)
    WHERE target_profile_id IS NOT NULL;

CREATE INDEX account_sanctions_source_report_idx
    ON account_sanctions(source_report_id)
    WHERE source_report_id IS NOT NULL;
