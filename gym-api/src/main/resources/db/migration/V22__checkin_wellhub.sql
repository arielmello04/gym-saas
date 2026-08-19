-- V22__checkin_wellhub.sql
-- 1) Gympass virou Wellhub: renomeia o valor do provider.
-- 2) Guarda a identificacao que o parceiro devolve na validacao, para
--    conciliacao com o repasse deles depois.

-- O CHECK do V1 fixava ('GYMPASS','TOTALPASS','DIRECT'); precisa sair antes do UPDATE.
ALTER TABLE checkins DROP CONSTRAINT IF EXISTS checkins_provider_check;

UPDATE checkins SET provider = 'WELLHUB' WHERE provider = 'GYMPASS';

ALTER TABLE checkins
    ADD CONSTRAINT checkins_provider_check
    CHECK (provider IN ('WELLHUB','TOTALPASS','DIRECT'));

-- Identificacao do aluno no parceiro (o "quem" do outro lado) e o plano dele.
ALTER TABLE checkins
    ADD COLUMN IF NOT EXISTS partner_member_ref VARCHAR(120),
    ADD COLUMN IF NOT EXISTS partner_plan       VARCHAR(120),
    ADD COLUMN IF NOT EXISTS failure_reason     VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_checkins_partner_member
    ON checkins(partner_member_ref) WHERE partner_member_ref IS NOT NULL;

-- Relatorio de repasse: quantos check-ins por parceiro, por dia, por academia.
CREATE INDEX IF NOT EXISTS idx_checkins_tenant_provider_started
    ON checkins(tenant_id, provider, started_at);
