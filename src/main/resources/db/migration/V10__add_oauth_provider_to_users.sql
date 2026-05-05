-- Migration to add OAuth2 provider fields to users table
ALTER TABLE users ADD COLUMN provider VARCHAR(20);
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Index for faster lookups during OAuth2 login
CREATE INDEX idx_users_provider_provider_id ON users(provider, provider_id);
