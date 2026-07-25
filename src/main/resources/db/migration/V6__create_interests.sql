CREATE TABLE interests (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL,
    description varchar(500),
    created_at timestamp NOT NULL
);

CREATE UNIQUE INDEX interests_name_lower_idx ON interests (lower(name));
