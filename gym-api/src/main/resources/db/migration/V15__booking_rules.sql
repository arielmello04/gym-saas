-- V15__booking_rules.sql
-- Adds configurable cancellation cutoff (in hours) and "one-per-day-per-type" toggle.

ALTER TABLE booking_config
  ADD COLUMN IF NOT EXISTS cancel_cutoff_hours INT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS one_per_day_per_type BOOLEAN NOT NULL DEFAULT TRUE;

-- Touch updated_at for visibility (optional)
UPDATE booking_config SET updated_at = NOW() WHERE id = TRUE;