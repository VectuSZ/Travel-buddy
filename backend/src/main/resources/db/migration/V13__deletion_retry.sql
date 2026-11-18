ALTER TABLE deletion_jobs ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX deletion_jobs_due_idx ON deletion_jobs(next_attempt_at);
