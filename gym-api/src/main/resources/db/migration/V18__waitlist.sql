-- =============================================================
-- V18: Waitlist (fila de espera) para sessões lotadas
-- =============================================================

CREATE TABLE IF NOT EXISTS waitlist_entries (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants(id),
    session_id   BIGINT       NOT NULL REFERENCES class_sessions(id),
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    position     INT          NOT NULL,                  -- posição na fila (1 = primeiro)
    status       VARCHAR(20)  NOT NULL DEFAULT 'WAITING', -- WAITING | PROMOTED | EXPIRED | CANCELED
    notified_at  TIMESTAMPTZ,                            -- quando foi notificado da vaga
    expires_at   TIMESTAMPTZ,                            -- prazo para confirmar após notificação
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_waitlist_active UNIQUE (session_id, user_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IF NOT EXISTS idx_waitlist_session  ON waitlist_entries(session_id, status, position);
CREATE INDEX IF NOT EXISTS idx_waitlist_user     ON waitlist_entries(user_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_waitlist_expires  ON waitlist_entries(expires_at) WHERE status = 'WAITING';

-- Configura tempo de expiração (horas) da vaga após notificação — por tenant
ALTER TABLE booking_config
    ADD COLUMN IF NOT EXISTS waitlist_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS waitlist_promotion_hours    INT     NOT NULL DEFAULT 2;
