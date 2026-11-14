-- Additive migration: retain the original columns and all legacy content.
-- Ownerless legacy records remain drafts until explicitly assigned in dev/test.
ALTER TABLE trips
 ADD COLUMN organizer_id UUID REFERENCES users(id),
 ADD COLUMN slug VARCHAR(160),
 ADD COLUMN itinerary TEXT,
 ADD COLUMN looking_for VARCHAR(2000),
 ADD COLUMN date_mode VARCHAR(16) CHECK (date_mode IN ('EXACT','FLEXIBLE')),
 ADD COLUMN date_from DATE,
 ADD COLUMN date_to DATE,
 ADD COLUMN duration_days SMALLINT CHECK (duration_days > 0),
 ADD COLUMN target_people SMALLINT CHECK (target_people BETWEEN 2 AND 50),
 ADD COLUMN budget_min NUMERIC(10,2) CHECK (budget_min >= 0),
 ADD COLUMN budget_max NUMERIC(10,2) CHECK (budget_max >= 0),
 ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'PLN' CHECK (currency = 'PLN'),
 ADD COLUMN styles TEXT[] NOT NULL DEFAULT '{}',
 ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','OPEN','CLOSED')),
 ADD COLUMN moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE' CHECK (moderation_status IN ('VISIBLE','HIDDEN')),
 ADD COLUMN published_at TIMESTAMPTZ,
 ADD COLUMN closed_at TIMESTAMPTZ,
 ADD COLUMN version INTEGER NOT NULL DEFAULT 0,
 ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ADD CONSTRAINT trip_dates CHECK (date_from <= date_to),
 ADD CONSTRAINT trip_budget CHECK (budget_min <= budget_max),
 ADD CONSTRAINT trip_duration CHECK (date_mode <> 'FLEXIBLE' OR duration_days <= date_to - date_from + 1),
 ADD CONSTRAINT trip_published_owner CHECK (status = 'DRAFT' OR organizer_id IS NOT NULL);
UPDATE trips SET itinerary = description, date_from = start_date, date_to = end_date, date_mode = 'EXACT';
CREATE TABLE trip_locations (
 id UUID PRIMARY KEY,
 trip_id UUID NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
 position SMALLINT NOT NULL CHECK (position >= 0),
 country_code CHAR(2) NOT NULL CHECK (country_code ~ '^[A-Z]{2}$'),
 city VARCHAR(120),
 UNIQUE (trip_id, position)
);
CREATE INDEX trip_locations_country_idx ON trip_locations(country_code, trip_id);
CREATE INDEX trips_public_recent_idx ON trips(published_at DESC, id DESC) WHERE status = 'OPEN' AND moderation_status = 'VISIBLE';
CREATE INDEX trips_public_dates_idx ON trips(date_from, date_to) WHERE status = 'OPEN' AND moderation_status = 'VISIBLE';
CREATE INDEX trips_public_budget_idx ON trips(budget_min) WHERE status = 'OPEN' AND moderation_status = 'VISIBLE';
CREATE INDEX trips_styles_idx ON trips USING gin(styles);
CREATE INDEX trips_organizer_idx ON trips(organizer_id, published_at);
