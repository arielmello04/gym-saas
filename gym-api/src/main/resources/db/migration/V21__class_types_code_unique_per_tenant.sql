-- V21__class_types_code_unique_per_tenant.sql
-- O V1 criou class_types.code como UNIQUE global, antes de existir multi-tenant.
-- O V16/V17 adicionaram tenant_id mas nao mexeram nessa restricao, entao duas
-- academias nunca puderam ter o mesmo codigo: a segunda a cadastrar "YOGA"
-- batia em constraint violation. Aqui a unicidade passa a ser por tenant.

-- O nome da constraint depende de como o Postgres a gerou; remove pelo nome
-- padrao e, na duvida, procura qualquer unique que cubra apenas (code).
DO $$
DECLARE
    v_constraint TEXT;
BEGIN
    SELECT con.conname INTO v_constraint
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'class_types'
      AND con.contype = 'u'
      AND con.conkey = ARRAY[
            (SELECT attnum FROM pg_attribute
              WHERE attrelid = rel.oid AND attname = 'code')
          ]::smallint[]
    LIMIT 1;

    IF v_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE class_types DROP CONSTRAINT %I', v_constraint);
    END IF;
END $$;

-- Mesmo codigo pode existir em academias diferentes, nunca duas vezes na mesma.
CREATE UNIQUE INDEX IF NOT EXISTS uk_class_types_tenant_code
    ON class_types(tenant_id, code);
