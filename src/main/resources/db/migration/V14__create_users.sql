CREATE TABLE users (
    id uuid PRIMARY KEY,
    email varchar(320) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role varchar(20) NOT NULL,
    created_at timestamp NOT NULL
);

CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));
