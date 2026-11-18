ALTER TABLE sessions ADD COLUMN deletion_confirmed_until TIMESTAMPTZ;
CREATE TABLE deletion_jobs (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 requested_at TIMESTAMPTZ NOT NULL
);
