-- =========================
-- BASE: users
-- =========================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- =========================
-- AUTH INVITE: signup_tokens (versão NOVA)
-- =========================
CREATE TABLE IF NOT EXISTS signup_tokens (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_signup_tokens_active ON signup_tokens(active);

-- =========================
-- BOOKING: class_types, class_sessions, bookings, policy
-- =========================
CREATE TABLE IF NOT EXISTS class_types (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS class_sessions (
    id BIGSERIAL PRIMARY KEY,
    class_type_id BIGINT NOT NULL REFERENCES class_types(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at   TIMESTAMPTZ NOT NULL,
    capacity INT NOT NULL,
    canceled BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(255),
    created_by_admin_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
-- evita duplicidade do mesmo tipo na mesma data/hora
CREATE UNIQUE INDEX IF NOT EXISTS uk_class_sessions_type_start
    ON class_sessions(class_type_id, start_at);

CREATE TABLE IF NOT EXISTS bookings (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES class_sessions(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(16) NOT NULL, -- BOOKED | CANCELED
    created_at TIMESTAMPTZ NOT NULL,
    canceled_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_bookings_session ON bookings(session_id);
CREATE INDEX IF NOT EXISTS idx_bookings_user ON bookings(user_id);

CREATE TABLE IF NOT EXISTS booking_policies (
    id BIGSERIAL PRIMARY KEY,
    open_days_in_advance INT NOT NULL,
    created_by_admin_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- Seed policy default
INSERT INTO booking_policies (open_days_in_advance, created_by_admin_id, created_at, updated_at)
SELECT 15, 0, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM booking_policies);

-- =========================
-- CHECK-IN (modelo FINAL)
-- =========================
CREATE TABLE IF NOT EXISTS checkins (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('GYMPASS','TOTALPASS','DIRECT')),
    gym_name VARCHAR(120),
    provider_ref VARCHAR(120),
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED','COMPLETED','FAILED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_checkins_user ON checkins(user_id);
CREATE INDEX IF NOT EXISTS idx_checkins_provider ON checkins(provider);

-- =========================
-- DOCUMENTS (apenas o modelo ATUAL: user_documents)
-- =========================
CREATE TABLE IF NOT EXISTS user_documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(100) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('DIET','MEDICAL','OTHER')),
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    storage_path VARCHAR(255) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_user_documents_user ON user_documents(user_id);

-- =========================
-- SUBSCRIPTIONS & PAYMENTS
-- =========================
CREATE TABLE IF NOT EXISTS subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    plan_name VARCHAR(64) NOT NULL,
    price_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    billing_day INT NOT NULL,
    status VARCHAR(16) NOT NULL, -- ACTIVE | PAST_DUE | CANCELED
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    next_billing_at      TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    canceled_at          TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_sub_user ON subscriptions(user_id);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES subscriptions(id),
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,  -- PENDING | PAID | FAILED
    provider VARCHAR(32) NOT NULL, -- "MOCK"
    provider_ref VARCHAR(128),
    due_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_pay_sub ON payments(subscription_id);

-- =========================
-- BOOKING CONFIG (single row)
-- =========================
CREATE TABLE IF NOT EXISTS booking_config (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE,
    publish_days_before_month INT NOT NULL DEFAULT 15,
    business_days VARCHAR(32) DEFAULT 'MON-SAT',
    business_start TIME NOT NULL DEFAULT '08:00',
    business_end   TIME NOT NULL DEFAULT '18:00',
    updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO booking_config (id, publish_days_before_month, updated_at)
SELECT TRUE, 15, NOW()
WHERE NOT EXISTS (SELECT 1 FROM booking_config);

-- =========================
-- SEED básico de class_types
-- =========================
INSERT INTO class_types (code, name, description, active)
VALUES
    ('PILATES',  'Pilates',            'Pilates small group sessions', TRUE),
    ('STRENGTH', 'Strength Training',  'Weight training classes',     TRUE),
    ('MASSAGE',  'Massage',            'Relaxing massage sessions',   TRUE)
ON CONFLICT (code) DO NOTHING;

-- =========================
-- PROFILE PREFERENCES
-- =========================

CREATE TABLE IF NOT EXISTS profile_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    allow_recording BOOLEAN NOT NULL DEFAULT TRUE,
    allow_photos BOOLEAN NOT NULL DEFAULT TRUE,
    allow_face_visibility BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL
);

