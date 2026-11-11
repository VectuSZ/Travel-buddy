CREATE TABLE auth_identities (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 provider VARCHAR(32) NOT NULL,
 issuer VARCHAR(255) NOT NULL,
 subject VARCHAR(255) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 UNIQUE (issuer, subject),
 UNIQUE (user_id, provider)
);
