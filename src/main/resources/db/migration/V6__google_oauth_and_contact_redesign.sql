-- V6: Google OAuth, Identity Verification, Trust System, and Contact Method Redesign
-- This migration supports:
-- 1. Google OAuth authentication
-- 2. Identity provider tracking (Google/Telegram)
-- 3. Trust and reporting system
-- 4. Multiple contact methods per trip/request
-- 5. Contact protection (revealed only after acceptance)

-- ============================================
-- USERS TABLE CHANGES
-- ============================================

-- Google OAuth fields
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id VARCHAR(255) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_email VARCHAR(255);

-- Identity verification tracking
-- identity_verified: TRUE if user authenticated via Google or Telegram (not email/password)
-- identity_provider: 'GOOGLE' or 'TELEGRAM' - which provider verified their identity
ALTER TABLE users ADD COLUMN IF NOT EXISTS identity_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS identity_provider VARCHAR(20);

-- Trust system columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS trust_score INTEGER NOT NULL DEFAULT 100;
ALTER TABLE users ADD COLUMN IF NOT EXISTS report_count INTEGER NOT NULL DEFAULT 0;

-- Account suspension
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(500);

-- Contact method preferences (which methods user has enabled)
ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_telegram_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS contact_phone_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Index for Google ID lookups
CREATE INDEX IF NOT EXISTS idx_users_google_id ON users(google_id);

-- ============================================
-- TRIPS TABLE CHANGES
-- ============================================

-- New multiple contact columns (support both Telegram and phone on same trip)
ALTER TABLE trips ADD COLUMN IF NOT EXISTS contact_telegram VARCHAR(50);
ALTER TABLE trips ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(20);

-- Note: Old contact_method and contact_value columns kept for backward compatibility
-- They will be removed in a future migration after data migration

-- ============================================
-- ITEM_REQUESTS TABLE CHANGES
-- ============================================

-- New multiple contact columns for sender
ALTER TABLE item_requests ADD COLUMN IF NOT EXISTS sender_contact_telegram VARCHAR(50);
ALTER TABLE item_requests ADD COLUMN IF NOT EXISTS sender_contact_phone VARCHAR(20);

-- Track when contact was revealed (when request was accepted)
ALTER TABLE item_requests ADD COLUMN IF NOT EXISTS contact_revealed_at TIMESTAMP;

-- Note: Old sender_contact_method and sender_contact_value columns kept for backward compatibility

-- ============================================
-- REPORTS TABLE (NEW)
-- ============================================

CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,

    -- Who is reporting whom
    reporter_user_id BIGINT NOT NULL REFERENCES users(id),
    reported_user_id BIGINT NOT NULL REFERENCES users(id),

    -- Context (optional - which request/trip caused the report)
    request_id BIGINT REFERENCES item_requests(id),
    trip_id BIGINT REFERENCES trips(id),

    -- Report details
    -- Reason values: PHONE_NOT_WORKING, TELEGRAM_NOT_EXIST, WRONG_PERSON,
    --                UNRESPONSIVE, INAPPROPRIATE_BEHAVIOR, SUSPECTED_SCAM, OTHER
    reason VARCHAR(50) NOT NULL,
    details TEXT,

    -- Status tracking
    -- Status values: PENDING, REVIEWED, RESOLVED, DISMISSED
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_notes TEXT,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES users(id)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_reports_reported_user ON reports(reported_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_reporter_user ON reports(reporter_user_id);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON reports(created_at);

-- ============================================
-- DATA MIGRATION: Update existing users
-- ============================================

-- Set identity_verified and identity_provider for existing Telegram users
UPDATE users
SET identity_verified = TRUE,
    identity_provider = 'TELEGRAM'
WHERE telegram_user_id IS NOT NULL
  AND identity_provider IS NULL;

-- Enable Telegram contact for users who have a username
UPDATE users
SET contact_telegram_enabled = TRUE
WHERE username IS NOT NULL
  AND username != ''
  AND contact_telegram_enabled = FALSE;

-- Enable phone contact for users who have a phone number
UPDATE users
SET contact_phone_enabled = TRUE
WHERE phone_number IS NOT NULL
  AND phone_number != ''
  AND contact_phone_enabled = FALSE;

-- ============================================
-- DATA MIGRATION: Migrate trip contact data to new columns
-- ============================================

-- Copy TELEGRAM contact_value to contact_telegram
UPDATE trips
SET contact_telegram = contact_value
WHERE contact_method = 'TELEGRAM'
  AND contact_value IS NOT NULL
  AND contact_telegram IS NULL;

-- Copy PHONE contact_value to contact_phone
UPDATE trips
SET contact_phone = contact_value
WHERE contact_method = 'PHONE'
  AND contact_value IS NOT NULL
  AND contact_phone IS NULL;

-- ============================================
-- DATA MIGRATION: Migrate item_request contact data to new columns
-- ============================================

-- Copy TELEGRAM sender_contact_value to sender_contact_telegram
UPDATE item_requests
SET sender_contact_telegram = sender_contact_value
WHERE sender_contact_method = 'TELEGRAM'
  AND sender_contact_value IS NOT NULL
  AND sender_contact_telegram IS NULL;

-- Copy PHONE sender_contact_value to sender_contact_phone
UPDATE item_requests
SET sender_contact_phone = sender_contact_value
WHERE sender_contact_method = 'PHONE'
  AND sender_contact_value IS NOT NULL
  AND sender_contact_phone IS NULL;

-- Set contact_revealed_at for already accepted requests
UPDATE item_requests
SET contact_revealed_at = updated_at
WHERE status = 'ACCEPTED'
  AND contact_revealed_at IS NULL;
