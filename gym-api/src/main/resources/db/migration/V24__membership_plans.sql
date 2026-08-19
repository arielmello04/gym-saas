-- V24__membership_plans.sql
-- Catalogo de planos por academia.
--
-- Motivo: a assinatura recebia planName, priceCents e currency do corpo da
-- requisicao e cobrava exatamente esse valor. Com @Min(100) no DTO, qualquer
-- aluno autenticado assinava o plano anual por R$ 1,00 mandando
-- priceCents: 100. O preco passa a morar no servidor, e a assinatura so
-- referencia o plano pelo id.
--
-- Tambem e o que faltava para a tela de Planos: o frontend chamava GET /plans,
-- que nunca existiu.

CREATE TABLE IF NOT EXISTS membership_plans (
    id              BIGSERIAL    PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL REFERENCES tenants(id),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    price_cents     BIGINT       NOT NULL CHECK (price_cents >= 0),
    currency        VARCHAR(8)   NOT NULL DEFAULT 'BRL',
    interval_months INT          NOT NULL DEFAULT 1 CHECK (interval_months >= 1),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Mesmo codigo pode existir em academias diferentes, nunca duas vezes na mesma.
CREATE UNIQUE INDEX IF NOT EXISTS uk_membership_plans_tenant_code
    ON membership_plans(tenant_id, code);

CREATE INDEX IF NOT EXISTS idx_membership_plans_tenant_active
    ON membership_plans(tenant_id, active, sort_order);

-- Liga a assinatura ao plano do catalogo. Fica nulo nas assinaturas antigas,
-- criadas quando o plano era texto livre - planName e price_cents continuam
-- gravados na assinatura de proposito, para o historico nao mudar se a academia
-- reajustar o plano depois.
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS plan_id BIGINT REFERENCES membership_plans(id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_plan ON subscriptions(plan_id);

-- Cada academia comeca com tres planos, espelhando o que o frontend mostrava
-- fixo. A academia edita ou desativa pelo painel.
INSERT INTO membership_plans
    (tenant_id, code, name, description, price_cents, currency, interval_months, sort_order)
SELECT t.id, v.code, v.name, v.description, v.price_cents, 'BRL', v.interval_months, v.sort_order
FROM tenants t
CROSS JOIN (VALUES
    ('MENSAL',     'Mensal',     'Acesso livre as aulas, cobranca todo mes.',        12900,  1, 1),
    ('TRIMESTRAL', 'Trimestral', 'Tres meses com desconto sobre o plano mensal.',    34900,  3, 2),
    ('ANUAL',      'Anual',      'Doze meses com o melhor preco por mes.',          119900, 12, 3)
) AS v(code, name, description, price_cents, interval_months, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM membership_plans mp WHERE mp.tenant_id = t.id
);
