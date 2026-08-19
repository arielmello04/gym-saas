-- V20__checkins_provider_ref_index.sql
-- Índice e unicidade em checkins.provider_ref, espelhando o que o V19 fez em payments.
--
-- Motivo: o callback do provedor (Wellhub/TotalPass) chega sem autenticação e sem
-- header de tenant. A referência é a única chave que ele carrega, então o lookup
-- passa a ser por provider_ref sozinho — o que exige unicidade para não haver
-- ambiguidade entre academias, e um índice para não varrer a tabela.

CREATE INDEX IF NOT EXISTS idx_checkins_provider_ref
    ON checkins(provider_ref) WHERE provider_ref IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_checkins_provider_ref
    ON checkins(provider_ref) WHERE provider_ref IS NOT NULL;
