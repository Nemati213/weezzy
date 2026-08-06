ALTER TABLE profiles
    DROP CONSTRAINT profiles_user_id_fk;

ALTER TABLE profiles
    ADD CONSTRAINT profiles_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE profiles
    ADD COLUMN deleted_at timestamp;
