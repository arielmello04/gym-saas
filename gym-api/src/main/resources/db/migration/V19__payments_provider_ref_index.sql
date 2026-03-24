-- =============================================================
-- V19: Índice em payments.provider_ref para lookup rápido de webhooks
--      e add attempt_count / last_attempt_at se ainda não existirem
-- =============================================================

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS attempt_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_payments_provider_ref
    ON payments(provider_ref) WHERE provider_ref IS NOT NULL;

-- Garantir que provider_ref seja único quando presente (evita processar 2x o mesmo webhook)
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_provider_ref
    ON payments(provider_ref) WHERE provider_ref IS NOT NULL;
