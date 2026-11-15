CREATE TABLE reports (
 id UUID PRIMARY KEY, reporter_id UUID REFERENCES users(id) ON DELETE SET NULL,
 reporter_name VARCHAR(120), reporter_email VARCHAR(254),
 target_trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
 target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
 target_url TEXT NOT NULL, category VARCHAR(32) NOT NULL,
 description VARCHAR(4000) NOT NULL, good_faith BOOLEAN NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('OPEN','RESOLVED','DISMISSED')),
 resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
 resolved_at TIMESTAMPTZ, resolution_reason TEXT,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX reports_status_idx ON reports(status,created_at);
CREATE TABLE moderation_actions (
 id UUID PRIMARY KEY, admin_id UUID REFERENCES users(id) ON DELETE SET NULL,
 report_id UUID REFERENCES reports(id) ON DELETE SET NULL,
 target_trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
 target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
 action VARCHAR(32) NOT NULL, reason TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX moderation_trip_idx ON moderation_actions(target_trip_id,created_at);
CREATE INDEX moderation_user_idx ON moderation_actions(target_user_id,created_at);
