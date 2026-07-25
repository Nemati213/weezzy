CREATE TABLE skills (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL,
    description varchar(500),
    created_at timestamp NOT NULL
);

CREATE UNIQUE INDEX skills_name_lower_idx ON skills (lower(name));
