CREATE TABLE lunch_requests (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    status VARCHAR(50) NOT NULL,
    topic VARCHAR(50) NOT NULL,
    comment VARCHAR(255),
    time_slot TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    cancelled_at TIMESTAMP WITHOUT TIME ZONE,
    extension_requested_at TIMESTAMP WITHOUT TIME ZONE,
    extension_count INTEGER NOT NULL DEFAULT 0 CHECK (extension_count >= 0)
);

CREATE INDEX idx_lunch_request_matching
ON lunch_requests (status, time_slot, location_id);

CREATE UNIQUE INDEX idx_lunch_request_single_active_per_profile
ON lunch_requests (profile_id)
WHERE status IN ('SEARCHING', 'EXTENSION_REQUESTED');

CREATE INDEX idx_lunch_request_extension_timeout
ON lunch_requests (extension_requested_at)
WHERE status = 'EXTENSION_REQUESTED';
