CREATE TABLE applications (
 id UUID PRIMARY KEY,
 trip_id UUID NOT NULL REFERENCES trips(id),
 applicant_id UUID NOT NULL REFERENCES users(id),
 message VARCHAR(2000) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','WITHDRAWN')),
 decided_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(trip_id,applicant_id)
);
CREATE INDEX applications_trip_idx ON applications(trip_id,status,created_at);
CREATE INDEX applications_applicant_idx ON applications(applicant_id,created_at DESC);
ALTER TABLE trips ADD COLUMN first_accepted_at TIMESTAMPTZ;
