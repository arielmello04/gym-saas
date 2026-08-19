-- V23__booking_config_backfill_tenants.sql
-- booking_config nasceu como tabela singleton (um registro para o sistema todo).
-- O V16/V17 deram tenant_id a ela, mas nada passou a criar a linha das academias
-- novas - e sem essa linha o BookingConfigService.get() estoura, derrubando
-- /my/bookings, /classes/calendar e /admin/booking-config para aquela academia.
--
-- Aqui as academias sem configuracao recebem uma com os mesmos padroes do V1.
-- A criacao daqui para frente passou a ser feita junto com o tenant, no
-- TenantService.

INSERT INTO booking_config (
    tenant_id,
    publish_days_before_month,
    business_days,
    business_start,
    business_end,
    cancel_cutoff_hours,
    one_per_day_per_type,
    waitlist_enabled,
    waitlist_promotion_hours,
    updated_at
)
SELECT
    t.id,
    15,
    'MON-SAT',
    '08:00',
    '18:00',
    0,
    TRUE,
    TRUE,
    2,
    NOW()
FROM tenants t
WHERE NOT EXISTS (
    SELECT 1 FROM booking_config bc WHERE bc.tenant_id = t.id
);
