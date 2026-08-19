# Gym System

<p align="center">
  <b>SaaS de gestão para academias</b><br>
  Backend em <b>Spring Boot</b> e frontend em <b>Angular</b>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-informational">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.5-success">
  <img alt="Angular" src="https://img.shields.io/badge/Angular-17-red">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Database-blue">
  <img alt="JWT" src="https://img.shields.io/badge/Auth-JWT-orange">
  <img alt="Multi-tenant" src="https://img.shields.io/badge/Multi--tenant-sim-9cf">
</p>

## Sobre o projeto

O **Gym System** é uma aplicação full stack voltada para a administração de academias.
O projeto foi desenvolvido com foco em organização, autenticação, controle de usuários e rotinas comuns do ambiente de academia, como planos, pagamentos, check-ins e agendamentos.

Uma instalação atende **várias academias** — cada uma com seus alunos, turmas, planos e financeiro isolados.

Além do uso prático, o sistema também foi construído como projeto de estudo e portfólio, aplicando conceitos reais de arquitetura backend, integração com frontend e boas práticas de desenvolvimento.

## Funcionalidades

- Cadastro e login de usuários, com autenticação JWT
- Controle de perfis e permissões em dois níveis: globais e por academia
- Gerenciamento de membros, planos e pagamentos
- Agenda de aulas, reservas e fila de espera com promoção automática
- Check-in de alunos, direto ou pelos parceiros **Wellhub** e **TotalPass**
- Upload e gerenciamento de documentos
- Multi-tenant com isolamento verificado por testes

## Tecnologias utilizadas

### Backend
- Java 21
- Spring Boot 3.5
- Spring Security
- JWT
- JPA / Hibernate
- Flyway
- Maven

### Frontend
- Angular 17 (standalone components)
- TypeScript
- SCSS
- Angular Router
- HttpClient

### Banco de dados
- PostgreSQL

## Estrutura do projeto

```bash
gym-system/
├── gym-api/       # Backend Spring Boot
└── gym-angular/   # Frontend Angular
```

## Como executar o projeto

### Pré-requisitos

- Java 21
- Node.js
- PostgreSQL (ou Docker)

O Maven não precisa estar instalado: use o wrapper (`./mvnw`).

### Banco de dados

```bash
docker compose -f gym-api/docker-compose.yml up -d db
```

O Flyway cria e migra o schema sozinho no primeiro start.

### Backend

```bash
./mvnw -pl gym-api spring-boot:run
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html

### Frontend

```bash
cd gym-angular
npm install
npm start
```

A aplicação estará disponível em http://localhost:4200

### Configuração

Variáveis de ambiente documentadas em [`gym-api/.env.example`](gym-api/.env.example); guia detalhado de desenvolvimento em [`gym-api/README-DEV.md`](gym-api/README-DEV.md).

---

## Multi-tenant

Toda requisição acontece no contexto de **uma** academia, identificada por:

1. Header `X-Tenant-ID: academia-fit`, ou
2. Subdomínio `academia-fit.gymsystem.com.br`, ou
3. O claim `tenantSlug` do próprio JWT, quando nenhum dos dois vem.

O `TenantResolutionFilter` resolve a academia e **verifica se o usuário autenticado tem vínculo ativo com ela** antes de deixar a requisição seguir. Sem vínculo, 403 — apontar o header para outra academia não dá acesso a ela. Papéis globais `ADMIN_WEB`/`ADMIN_APP` administram qualquer academia; os demais só a sua.

Dois níveis de papel:

- **Global** (`UserRole`): `USER`, `ADMIN_APP`, `ADMIN_WEB`
- **Por academia** (`TenantRole`): `OWNER`, `MANAGER`, `STAFF`, `TRAINER`, `MEMBER`

## Módulos da API

| Pacote | Responsabilidade |
|---|---|
| `auth` | Login/signup JWT, cadastro por token de convite, bootstrap do primeiro admin |
| `tenant` | Academias, vínculos de usuário, resolução e isolamento de contexto |
| `booking` | Tipos de aula, sessões, reservas, políticas, gerador de agenda mensal, calendário |
| `booking.waitlist` | Fila de espera com promoção automática e prazo de confirmação |
| `checkin` | Check-in direto e via parceiros, conciliação administrativa |
| `checkin.partner` | Integrações **Wellhub** (consulta) e **TotalPass** (webhook), vínculos e fila de entradas |
| `payments` | Assinaturas, cobranças, régua de inadimplência, relatórios |
| `payments.plan` | Catálogo de planos de mensalidade por academia |
| `payments.gateway` | Mock, Mercado Pago, Pagar.me, Stripe |
| `documents` | Upload de arquivos do aluno (exames, contratos) |
| `profile`, `user`, `notifications`, `i18n`, `common` | Preferências, `/me`, e-mail, mensagens PT-BR, rate limit, erros |

## Check-in por parceiro (Wellhub e TotalPass)

Os dois resolvem o mesmo problema por caminhos **opostos**, e o código reflete isso: `ValidatingPartner` para quem a academia consulta, `PushPartner` para quem avisa por webhook.

**Wellhub** — o aluno faz check-in no aplicativo e a academia valida enviando o `gympass_id` dele:

```
POST {base}/validate
Authorization: Bearer {token}
X-Gym-Id: {gym_id}
{ "gympass_id": "1234567890123", "custom_code": "PIN4242" }
```

**TotalPass** — o parceiro chama a academia com um link exclusivo de confirmação, válido por **90 minutos**:

```
POST /api/v1/checkin/webhook/totalpass/{slug}/{segredo}
```

O evento fica guardado como pendente até alguém liberar na tela de **Parceiros**.

### Credenciais: metade da integradora, metade da academia

O token do Wellhub e a `partner_api_key` da TotalPass identificam a **integradora** e valem para toda a instalação — ficam em variável de ambiente. O **Gym ID** (Wellhub) e a **place_api_key** (TotalPass) são de **cada academia** e ficam no banco, por tenant.

Essa separação não é preferência: o token do Wellhub cobre várias unidades e o `X-Gym-Id` escolhe qual; a `partner_api_key` da TotalPass, segundo a documentação deles, nunca deve ser pedida ao cliente.

### Modo mock

Padrão em ambos: valida sem chamar o parceiro, para desenvolver antes de ter credencial. Identificador começando com `DENY` é recusado, para exercitar o caminho de erro.

> **Para ligar em produção** você precisa das credenciais, que vêm de processo comercial. O passo a passo completo está em [`docs/PARCEIROS.md`](docs/PARCEIROS.md).

## Planos e pagamentos

Cada academia tem seu **catálogo de planos**. O aluno assina mandando só o `planId`; nome, preço e moeda saem do catálogo no servidor.

Gateway escolhido por configuração (`PAYMENT_GATEWAY`): `mock`, `mercadopago`, `pagarme` ou `stripe`. Webhook único com verificação de assinatura HMAC-SHA256 em tempo constante.

## Painel web

Angular 17 standalone, tema escuro com acento volt reservado para ação. O design system mora em [`gym-angular/src/styles.scss`](gym-angular/src/styles.scss).

Telas: agenda com métricas e ocupação por aula, minhas aulas, fila de espera, check-in, planos, assinatura, perfil, e a área de administração (membros, planos, parceiros, assinaturas).

## Testes

```bash
./mvnw test
```

141 testes, sem dependência de banco ou de rede. Cobrem as regras que decidem acesso e dinheiro: isolamento entre academias, preço vindo do catálogo, credencial de parceiro por academia, regras de reserva e fila de espera, e assinatura de webhook.

Roda no CI a cada push e PR ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)), junto com o build de produção do Angular.

## Objetivo do projeto

Este projeto foi criado com foco em:

- prática de desenvolvimento full stack
- construção de portfólio
- aplicação de autenticação e autorização
- organização em camadas no backend
- integração entre API REST e frontend moderno

## Melhorias futuras

- testes de integração com banco real (Testcontainers)
- desligar `spring.jpa.open-in-view` (exige revisar os acessos lazy fora de transação)
- obter credenciais de parceiro e ligar Wellhub e TotalPass em produção
- dockerização completa e deploy

Roadmap mais amplo em [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Autor

Desenvolvido por **Ariel Melo**.
