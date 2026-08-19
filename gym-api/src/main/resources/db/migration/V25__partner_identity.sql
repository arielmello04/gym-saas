-- V25__partner_identity.sql
-- Identidade do aluno nos parceiros, e a caixa de entrada de check-ins do TotalPass.
--
-- Motivo: a primeira versao da integracao assumia que o aluno digitava um codigo
-- na recepcao. A documentacao dos dois parceiros mostra outro fluxo:
--
--   Wellhub  - o aluno faz check-in no app; a academia valida enviando o
--              gympass_id dele, que precisa estar guardado desde a primeira
--              visita. (POST /access/v1/validate)
--   TotalPass- o aluno faz check-in no app e a TOTALPASS chama a academia por
--              webhook, com um link exclusivo de confirmacao que vale 90 min.
--
-- Ou seja: em ambos e preciso saber quem e o aluno do lado de la, e no TotalPass
-- e preciso guardar o check-in que chegou ate alguem confirmar.

-- ── Quem e o aluno no parceiro ───────────────────────────────
CREATE TABLE IF NOT EXISTS partner_member_links (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants(id),
    user_id      BIGINT       NOT NULL REFERENCES users(id),
    provider     VARCHAR(20)  NOT NULL CHECK (provider IN ('WELLHUB','TOTALPASS')),

    -- Wellhub: gympass_id (13 digitos). TotalPass: o code do usuario no payload.
    external_id  VARCHAR(64)  NOT NULL,

    -- Wellhub: codigo interno (PIN/QR) que a academia associa ao aluno la.
    custom_code  VARCHAR(13),

    -- TotalPass identifica a pessoa por CPF no payload do webhook.
    document     VARCHAR(32),

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Um vinculo por aluno em cada parceiro, dentro da academia.
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_link_tenant_user_provider
    ON partner_member_links(tenant_id, user_id, provider);

-- O mesmo id de parceiro nao pode apontar para dois alunos da mesma academia.
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_link_tenant_provider_external
    ON partner_member_links(tenant_id, provider, external_id);

-- Casar o webhook do TotalPass, que chega identificado por CPF.
CREATE INDEX IF NOT EXISTS idx_partner_link_document
    ON partner_member_links(tenant_id, provider, document) WHERE document IS NOT NULL;

-- ── Check-ins que o parceiro empurrou (TotalPass) ────────────
CREATE TABLE IF NOT EXISTS partner_checkin_events (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenants(id),
    provider        VARCHAR(20)  NOT NULL CHECK (provider IN ('WELLHUB','TOTALPASS')),

    -- URL exclusiva de confirmacao que veio no payload. E a chave do evento:
    -- a mesma notificacao reenviada nao pode virar duas entradas.
    confirm_url     TEXT         NOT NULL,

    external_user   VARCHAR(64),
    user_name       VARCHAR(160),
    user_document   VARCHAR(32),
    plan_code       VARCHAR(64),
    place_code      VARCHAR(64),

    started_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,

    -- PENDING: chegou e aguarda confirmacao. CONFIRMED: liberamos a entrada.
    -- EXPIRED: passou dos 90 min. FAILED: o parceiro recusou a confirmacao.
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','CONFIRMED','EXPIRED','FAILED')),
    failure_reason  VARCHAR(255),

    -- Check-in gerado no nosso lado quando a entrada e confirmada.
    checkin_id      BIGINT       REFERENCES checkins(id),
    user_id         BIGINT       REFERENCES users(id),

    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_event_confirm_url
    ON partner_checkin_events(confirm_url);

CREATE INDEX IF NOT EXISTS idx_partner_event_pending
    ON partner_checkin_events(tenant_id, status, received_at);

CREATE INDEX IF NOT EXISTS idx_partner_event_expiry
    ON partner_checkin_events(expires_at) WHERE status = 'PENDING';
