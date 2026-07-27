ALTER TABLE profiles
    ADD COLUMN user_id uuid;

ALTER TABLE profiles
    ADD CONSTRAINT profiles_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id);

CREATE UNIQUE INDEX profiles_user_id_idx ON profiles(user_id);
