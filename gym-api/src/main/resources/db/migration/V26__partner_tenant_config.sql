-- V26__partner_tenant_config.sql
-- Credenciais de parceiro que sao DE CADA ACADEMIA.
--
-- A divisao vem da propria documentacao dos dois parceiros:
--
--   Wellhub   - um bearer token identifica a integradora "e as academias que
--               voce tem permissao de validar"; o X-Gym-Id escolhe qual delas.
--   TotalPass - a partner_api_key e do ERP e "nunca deve ser solicitada aos seus
--               clientes"; a place_api_key e a propria academia que gera no
--               portal, aba Integracoes.
--
-- Ou seja: token e partner_api_key sao da integradora e seguem em configuracao
-- global; gym_id e place_api_key sao por academia e precisavam sair de la. Com
-- eles no application.yml, a instalacao inteira so atendia UMA academia - a
-- segunda teria as visitas creditadas para a primeira.

CREATE TABLE IF NOT EXISTS partner_tenant_configs (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants(id),
    provider      VARCHAR(20)  NOT NULL CHECK (provider IN ('WELLHUB','TOTALPASS')),

    -- Wellhub: valor do header X-Gym-Id desta unidade. Nao e segredo.
    gym_id        VARCHAR(128),

    -- TotalPass: chave da unidade, gerada pela academia no portal. E segredo.
    place_api_key VARCHAR(255),

    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Uma configuracao por parceiro em cada academia.
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_tenant_config
    ON partner_tenant_configs(tenant_id, provider);

-- Duas academias nao podem apontar para a mesma unidade no parceiro: seria
-- exatamente o erro de credito que esta migration existe para impedir.
CREATE UNIQUE INDEX IF NOT EXISTS uk_partner_tenant_gym_id
    ON partner_tenant_configs(provider, gym_id) WHERE gym_id IS NOT NULL;
