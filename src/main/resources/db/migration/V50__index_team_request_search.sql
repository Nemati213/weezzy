CREATE INDEX idx_team_requests_open_created
    ON team_requests (created_at DESC, id) WHERE status = 'OPEN';
