CREATE TABLE profile_photos (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    position INT NOT NULL DEFAULT 0 CHECK (position >= 0),
    is_avatar BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'READY')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    uploaded_at TIMESTAMP
);

CREATE INDEX idx_profile_photos_profile_id ON profile_photos(profile_id);

CREATE UNIQUE INDEX uq_profile_photos_avatar
    ON profile_photos(profile_id)
    WHERE is_avatar = TRUE;
