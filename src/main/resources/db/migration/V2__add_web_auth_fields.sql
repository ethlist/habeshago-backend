-- V2: Add web authentication fields for standalone website users

-- Make telegram_user_id nullable (web users don't have Telegram ID)
ALTER TABLE users ALTER COLUMN telegram_user_id DROP NOT NULL;

-- Add email and password hash columns for web authentication
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- Index for email lookups
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email) WHERE email IS NOT NULL;
