-- V7: Account deletion support and OAuth ID tracking
-- Implements: B1 (soft delete fields) and B2 (OAuth tracking table)

-- Add soft delete fields to users table
ALTER TABLE users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN deletion_reason VARCHAR(20); -- USER_REQUEST, ADMIN_BAN, SUSPENDED
ALTER TABLE users ADD COLUMN anonymized BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN anonymized_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN retention_until TIMESTAMP WITH TIME ZONE;

-- Add indexes for soft delete queries and scheduled jobs
CREATE INDEX idx_users_deleted ON users(deleted);
CREATE INDEX idx_users_deleted_at ON users(deleted_at);
CREATE INDEX idx_users_retention_until ON users(retention_until);
CREATE INDEX idx_users_deleted_anonymized ON users(deleted, anonymized);

-- Create OAuth ID tracking table
-- This table survives user hard-deletion to prevent account recreation abuse
CREATE TABLE oauth_id_records (
    id BIGSERIAL PRIMARY KEY,
    google_id VARCHAR(255) UNIQUE,
    telegram_user_id BIGINT UNIQUE,
    user_id BIGINT, -- NOT a foreign key - user may be deleted
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    blocked_until TIMESTAMP WITH TIME ZONE,
    permanently_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for OAuth tracking lookups
CREATE INDEX idx_oauth_records_google_id ON oauth_id_records(google_id);
CREATE INDEX idx_oauth_records_telegram_user_id ON oauth_id_records(telegram_user_id);
CREATE INDEX idx_oauth_records_user_id ON oauth_id_records(user_id);
CREATE INDEX idx_oauth_records_deleted ON oauth_id_records(deleted);
CREATE INDEX idx_oauth_records_blocked_until ON oauth_id_records(blocked_until);

-- Populate oauth_id_records with existing users (for tracking)
INSERT INTO oauth_id_records (google_id, telegram_user_id, user_id, created_at, updated_at)
SELECT
    google_id,
    telegram_user_id,
    id,
    created_at,
    NOW()
FROM users
WHERE google_id IS NOT NULL OR telegram_user_id IS NOT NULL;
