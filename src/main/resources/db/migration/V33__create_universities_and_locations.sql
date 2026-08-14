CREATE TABLE universities (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX universities_name_city_lower_idx
ON universities (LOWER(name), LOWER(city));

CREATE TABLE locations (
    id UUID PRIMARY KEY,
    university_id UUID NOT NULL REFERENCES universities(id) ON DELETE RESTRICT,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_location_type ON locations (type);
CREATE INDEX idx_location_university_id ON locations (university_id);

CREATE UNIQUE INDEX locations_university_name_address_lower_idx
ON locations (university_id, LOWER(name), LOWER(address));
