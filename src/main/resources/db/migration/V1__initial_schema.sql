-- HabeshaGo Initial Schema
-- V1: Create all core tables (PostgreSQL)

-- Users table with reputation fields
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    username VARCHAR(255),
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'en',

    -- Verification fields
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,

    -- Reputation fields (denormalized for fast reads)
    rating_average DOUBLE PRECISION,
    rating_count INTEGER NOT NULL DEFAULT 0,
    completed_trips_count INTEGER NOT NULL DEFAULT 0,
    completed_deliveries_count INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_telegram_id ON users(telegram_user_id);
CREATE INDEX IF NOT EXISTS idx_user_verified ON users(verified) WHERE verified = TRUE;

-- Trips table
CREATE TABLE IF NOT EXISTS trips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),

    from_city VARCHAR(100) NOT NULL,
    from_country VARCHAR(100),
    from_airport_code VARCHAR(10),

    to_city VARCHAR(100) NOT NULL,
    to_country VARCHAR(100),
    to_airport_code VARCHAR(10),

    departure_date DATE NOT NULL,
    arrival_date DATE,

    capacity_type VARCHAR(20) NOT NULL,
    max_weight_kg DECIMAL(10,2),
    notes VARCHAR(2000),

    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trip_search ON trips(from_city, to_city, departure_date);
CREATE INDEX IF NOT EXISTS idx_trip_user ON trips(user_id);
CREATE INDEX IF NOT EXISTS idx_trip_status ON trips(status);

-- Item requests table
CREATE TABLE IF NOT EXISTS item_requests (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    sender_user_id BIGINT NOT NULL REFERENCES users(id),

    description VARCHAR(2000) NOT NULL,
    weight_kg DECIMAL(10,2),
    special_instructions VARCHAR(2000),

    pickup_photo_url VARCHAR(500),
    delivery_photo_url VARCHAR(500),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_request_trip ON item_requests(trip_id);
CREATE INDEX IF NOT EXISTS idx_request_sender ON item_requests(sender_user_id);
CREATE INDEX IF NOT EXISTS idx_request_status ON item_requests(status);

-- Reviews table
CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL REFERENCES trips(id),
    item_request_id BIGINT NOT NULL REFERENCES item_requests(id),
    reviewer_id BIGINT NOT NULL REFERENCES users(id),
    reviewed_traveler_id BIGINT NOT NULL REFERENCES users(id),

    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(2000),

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT unique_review_per_request UNIQUE (item_request_id, reviewer_id)
);

CREATE INDEX IF NOT EXISTS idx_review_traveler ON reviews(reviewed_traveler_id);
CREATE INDEX IF NOT EXISTS idx_review_created ON reviews(created_at DESC);

-- Notification outbox table (for reliable Telegram notifications)
CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),

    type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_error TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON notification_outbox(status);
CREATE INDEX IF NOT EXISTS idx_outbox_next_attempt ON notification_outbox(next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON notification_outbox(status, next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_outbox_user ON notification_outbox(user_id);
