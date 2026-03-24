-- =============================================================
-- V17: Backfill existing rows to the 'default' tenant
-- Then enforce NOT NULL on tenant_id columns
-- =============================================================

DO $$
DECLARE
    v_tenant_id BIGINT;
BEGIN
    SELECT id INTO v_tenant_id FROM tenants WHERE slug = 'default';

    -- backfill each table
    UPDATE class_types         SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE class_sessions      SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE bookings            SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE booking_policies    SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE booking_config      SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE checkins            SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE user_documents      SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE subscriptions       SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE payments            SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE profile_preferences SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
    UPDATE signup_tokens       SET tenant_id = v_tenant_id WHERE tenant_id IS NULL;
END $$;

-- Now enforce NOT NULL (safe after backfill)
ALTER TABLE class_types         ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE class_sessions      ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE bookings            ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE booking_policies    ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE booking_config      ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE checkins            ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE user_documents      ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE subscriptions       ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE payments            ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE profile_preferences ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE signup_tokens       ALTER COLUMN tenant_id SET NOT NULL;

-- Unique index: one BookingConfig per tenant (replaces the single-row constraint)
ALTER TABLE booking_config DROP CONSTRAINT IF EXISTS booking_config_pkey;
ALTER TABLE booking_config DROP COLUMN IF EXISTS id;
ALTER TABLE booking_config ADD COLUMN id BIGSERIAL PRIMARY KEY;
CREATE UNIQUE INDEX IF NOT EXISTS uk_booking_config_tenant ON booking_config(tenant_id);
