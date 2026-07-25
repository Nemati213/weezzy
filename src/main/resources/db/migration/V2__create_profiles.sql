CREATE TABLE profiles (
    id uuid PRIMARY KEY,
    display_name varchar(80) NOT NULL,
    bio varchar(500),
    telegram varchar(64),
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL
);
