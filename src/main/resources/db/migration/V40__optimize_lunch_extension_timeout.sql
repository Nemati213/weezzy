DROP INDEX idx_lunch_request_extension_timeout;

CREATE INDEX idx_lunch_request_extension_timeout
ON lunch_requests (extension_expires_at, id)
WHERE status = 'EXTENSION_REQUESTED';
