CREATE TABLE lunch_groups (
    id UUID PRIMARY KEY,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
    time_slot TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    topic VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    cancelled_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_lunch_groups_status_time_slot
ON lunch_groups (status, time_slot);

CREATE INDEX idx_lunch_groups_location_time_slot
ON lunch_groups (location_id, time_slot);
