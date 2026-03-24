# Roadmap sugerido para um SaaS de academias (backend)

Este projeto já tem módulos importantes (auth/JWT, booking, check-in, documentos, pagamentos mock).
Para virar **SaaS multi-academias**, normalmente faltam estes blocos:

## 1) Multi‑tenant (o “SaaS” de verdade)
**Opções de desenho:**
- (A) `tenant_id` em todas as tabelas (mais simples)
- (B) schema por tenant (mais isolado, mais complexo)
- (C) database por tenant (mais caro, ainda mais isolamento)

MVP recomendado: (A) `tenant_id` + filtros por tenant no backend.

Entidades iniciais:
- `tenants` (academia)
- `tenant_users` (vínculo usuário ↔ academia + papel)
- `plans` (plano da academia, ex.: Basic/Pro) + limites

## 2) Plano/mensalidade do aluno (membership)
- `members` (perfil do aluno dentro do tenant)
- `membership_plans` (mensal, trimestral, anual)
- `subscriptions` (status: active/past_due/canceled)
- Regras: bloqueio de check-in se vencido

## 3) Financeiro
- `invoices` / `charges`
- Integração de pagamento real (Pix/cartão) via gateway (ex.: Mercado Pago, Pagar.me, Stripe)
- Webhooks com assinatura HMAC

## 4) Acesso e permissões
- Roles por tenant: OWNER, MANAGER, STAFF, TRAINER, MEMBER
- Auditoria de ações (logs)

## 5) Operação da academia
- Turmas/agenda (já existe) + lista de presença
- Controle de capacidade, fila de espera
- Registro de treino / fichas (opcional para MVP)

## 6) Observabilidade e qualidade
- Testes de integração com Testcontainers
- Seed de dados para dev
- Documentação de arquitetura + diagramas

---

## Próximo passo recomendado
1) Rodar o projeto localmente (IntelliJ + Postgres)
2) Garantir fluxo Auth (admin bootstrap → invite token → signup → login)
3) Mapear o que já existe nos endpoints
4) Implementar **Tenant** (multi-tenant) antes do frontend, para não retrabalhar telas e permissões
