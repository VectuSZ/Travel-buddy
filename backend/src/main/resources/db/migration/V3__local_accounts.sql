CREATE TABLE users (
 id UUID PRIMARY KEY,
 email VARCHAR(254) NOT NULL,
 password_hash TEXT,
 email_verified_at TIMESTAMPTZ,
 role VARCHAR(16) NOT NULL CHECK (role IN ('USER','ADMIN')),
 status VARCHAR(24) NOT NULL CHECK (status IN ('ACTIVE','BLOCKED','DELETION_PENDING')),
 ui_locale VARCHAR(5) NOT NULL CHECK (ui_locale IN ('pl','en')),
 terms_version VARCHAR(32) NOT NULL,
 terms_accepted_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX users_email_unique ON users (lower(email));
CREATE TABLE sessions (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash BYTEA NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 authenticated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX sessions_user ON sessions(user_id);
CREATE INDEX sessions_expiry ON sessions(expires_at);
CREATE TABLE auth_tokens (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 purpose VARCHAR(24) NOT NULL CHECK (purpose = 'PASSWORD_RESET'),
 token_hash BYTEA NOT NULL UNIQUE,
 created_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ
);
CREATE INDEX auth_tokens_user ON auth_tokens(user_id);
CREATE INDEX auth_tokens_expiry ON auth_tokens(expires_at);
