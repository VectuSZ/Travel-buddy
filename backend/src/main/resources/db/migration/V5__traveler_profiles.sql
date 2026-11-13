CREATE TABLE traveler_profiles (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 display_name VARCHAR(60) NOT NULL CHECK (length(trim(display_name)) BETWEEN 2 AND 60),
 age SMALLINT NOT NULL CHECK (age BETWEEN 18 AND 120),
 age_updated_at DATE NOT NULL,
 bio VARCHAR(1000),
 languages TEXT[] NOT NULL CHECK (cardinality(languages) BETWEEN 1 AND 10),
 travel_styles TEXT[] NOT NULL CHECK (cardinality(travel_styles) BETWEEN 1 AND 4),
 version INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE profile_contacts (
 user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
 contact_email VARCHAR(254), instagram_handle VARCHAR(30), whatsapp_e164 VARCHAR(16),
 updated_at TIMESTAMPTZ NOT NULL
);
