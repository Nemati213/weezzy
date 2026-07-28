CREATE TABLE interest_suggestions (
    id UUID PRIMARY KEY,
    suggested_by_user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    reviewed_by_user_id UUID REFERENCES users(id),
    CONSTRAINT ck_interest_suggestions_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_interest_suggestions_user_id ON interest_suggestions(suggested_by_user_id);
CREATE INDEX idx_interest_suggestions_status_created_at
    ON interest_suggestions(status, created_at);
CREATE UNIQUE INDEX ux_interest_suggestions_pending_user_name
    ON interest_suggestions (suggested_by_user_id, lower(name))
    WHERE status = 'PENDING';
