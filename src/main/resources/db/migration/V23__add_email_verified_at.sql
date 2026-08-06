ALTER TABLE users
    ADD COLUMN email_verified_at timestamp;

UPDATE users
SET email_verified_at = created_at
WHERE email_verified_at IS NULL;
