ALTER TABLE teams ADD COLUMN purpose VARCHAR(2000);
UPDATE teams SET purpose = COALESCE(NULLIF(TRIM(description), ''), name);
ALTER TABLE teams ALTER COLUMN purpose SET NOT NULL;
ALTER TABLE teams ADD CONSTRAINT teams_purpose_not_blank
    CHECK (LENGTH(TRIM(purpose)) > 0);

CREATE TABLE team_positions (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    description VARCHAR(2000),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (id, team_id)
);
CREATE INDEX idx_team_positions_team ON team_positions (team_id, created_at, id);

INSERT INTO team_positions (id, team_id, name, description, capacity, created_at, updated_at)
SELECT p.id, r.team_id, p.name, p.description, p.slots_count, p.created_at, p.updated_at
FROM team_request_positions p JOIN team_requests r ON r.id = p.request_id;

CREATE TABLE team_position_skills (
    position_id UUID NOT NULL REFERENCES team_positions(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    PRIMARY KEY (position_id, skill_id)
);
CREATE INDEX idx_team_position_skills_skill ON team_position_skills (skill_id, position_id);
INSERT INTO team_position_skills SELECT position_id, skill_id
FROM team_request_position_skills;
DROP TABLE team_request_position_skills;

ALTER TABLE team_request_positions ADD COLUMN team_position_id UUID;
UPDATE team_request_positions SET team_position_id = id;
ALTER TABLE team_request_positions ALTER COLUMN team_position_id SET NOT NULL;
ALTER TABLE team_request_positions ADD CONSTRAINT request_position_team_position_fk
    FOREIGN KEY (team_position_id) REFERENCES team_positions(id) ON DELETE RESTRICT;
ALTER TABLE team_request_positions ADD CONSTRAINT uq_request_team_position
    UNIQUE (request_id, team_position_id);
ALTER TABLE team_request_positions DROP COLUMN name, DROP COLUMN description,
    DROP COLUMN slots_count, DROP COLUMN updated_at;
CREATE INDEX idx_request_positions_team_position ON team_request_positions (team_position_id);

ALTER TABLE team_members DROP CONSTRAINT team_members_position_id_fkey;
ALTER TABLE team_invitations ADD COLUMN position_id UUID;

CREATE TEMP TABLE legacy_team_positions AS
SELECT t.id AS team_id, gen_random_uuid() AS position_id FROM teams t
WHERE EXISTS (SELECT 1 FROM team_members m WHERE m.team_id = t.id AND m.position_id IS NULL)
   OR EXISTS (SELECT 1 FROM team_invitations i WHERE i.team_id = t.id);

INSERT INTO team_positions (id, team_id, name, capacity)
SELECT l.position_id, l.team_id, 'Позиция не указана (перенос)',
    GREATEST(1, (SELECT COUNT(*) FROM team_members m
                WHERE m.team_id = l.team_id AND m.position_id IS NULL)
        + (SELECT COUNT(DISTINCT i.invitee_profile_id) FROM team_invitations i
           WHERE i.team_id = l.team_id AND i.status = 'PENDING'
             AND NOT EXISTS (SELECT 1 FROM team_members m
                             WHERE m.team_id = i.team_id
                               AND m.profile_id = i.invitee_profile_id)))::INTEGER
FROM legacy_team_positions l;
UPDATE team_members m SET position_id = l.position_id FROM legacy_team_positions l
WHERE m.team_id = l.team_id AND m.position_id IS NULL;
UPDATE team_invitations i SET position_id = l.position_id FROM legacy_team_positions l
WHERE i.team_id = l.team_id;
DROP TABLE legacy_team_positions;

ALTER TABLE team_members ALTER COLUMN position_id SET NOT NULL;
ALTER TABLE team_members ADD CONSTRAINT team_member_position_fk
    FOREIGN KEY (position_id, team_id) REFERENCES team_positions(id, team_id) ON DELETE RESTRICT;
ALTER TABLE team_invitations ALTER COLUMN position_id SET NOT NULL;
ALTER TABLE team_invitations ADD CONSTRAINT team_invitation_position_fk
    FOREIGN KEY (position_id, team_id) REFERENCES team_positions(id, team_id) ON DELETE RESTRICT;
CREATE INDEX idx_team_invitations_position ON team_invitations (position_id);
ALTER TABLE team_requests DROP COLUMN target_team_size;
