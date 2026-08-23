CREATE INDEX idx_lunch_groups_completed_chat_cleanup
ON lunch_groups (completed_at, id)
WHERE status = 'COMPLETED' AND completed_at IS NOT NULL;

CREATE INDEX idx_lunch_groups_cancelled_chat_cleanup
ON lunch_groups (cancelled_at, id)
WHERE status = 'CANCELLED' AND cancelled_at IS NOT NULL;
