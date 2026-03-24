-- =============================================================
-- V16: Multi-tenant support
-- Strategy: shared schema with tenant_id on every entity table
-- =============================================================

-- =========================
-- TENANTS
-- =========================
CREATE TABLE IF NOT EXISTS tenants (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(64)  NOT NULL UNIQUE,   -- URL-safe identifier, e.g. "academia-fit"
    name        VARCHAR(128) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    plan        VARCHAR(32)  NOT NULL DEFAULT 'BASIC',  -- BASIC | PRO | ENTERPRISE
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenants_slug ON tenants(slug);

-- =========================
-- TENANT USERS (role per tenant)
-- One user can belong to multiple tenants with different roles
-- =========================
CREATE TABLE IF NOT EXISTS tenant_users (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     BIGINT       NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    role        VARCHAR(32)  NOT NULL,  -- OWNER | MANAGER | STAFF | TRAINER | MEMBER
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    joined_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tenant_users UNIQUE (tenant_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_tenant_users_tenant ON tenant_users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_users_user   ON tenant_users(user_id);

-- =========================
-- ADD tenant_id TO ALL ENTITY TABLES
-- Existing rows get tenant_id = NULL (pre-tenant data).
-- After backfill, add NOT NULL constraint in a future migration.
-- =========================

ALTER TABLE class_types       ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE class_sessions    ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE bookings          ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE booking_policies  ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE booking_config    ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE checkins          ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE user_documents    ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE subscriptions     ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE payments          ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE profile_preferences ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);
ALTER TABLE signup_tokens     ADD COLUMN IF NOT EXISTS tenant_id BIGINT REFERENCES tenants(id);

-- =========================
-- PERFORMANCE INDEXES ON tenant_id
-- =========================
CREATE INDEX IF NOT EXISTS idx_class_types_tenant    ON class_types(tenant_id);
CREATE INDEX IF NOT EXISTS idx_class_sessions_tenant ON class_sessions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_bookings_tenant        ON bookings(tenant_id);
CREATE INDEX IF NOT EXISTS idx_checkins_tenant        ON checkins(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant   ON subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payments_tenant        ON payments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_documents_tenant  ON user_documents(tenant_id);

-- =========================
-- SEED: default tenant for existing (pre-SaaS) data
-- All existing rows will be linked to this tenant by V17
-- =========================
INSERT INTO tenants (slug, name, active, plan, created_at, updated_at)
VALUES ('default', 'Default Gym', TRUE, 'PRO', NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;
