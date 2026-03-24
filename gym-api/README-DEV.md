# GymSystem – guia rápido (IntelliJ + Docker – Windows)

Este guia assume **Java 21**, **IntelliJ IDEA** e **Docker Desktop**.

## 1) Pré‑requisitos (uma vez)
1. Instale o **JDK 21** (Temurin/Adoptium ou Oracle).
2. Instale o **Docker Desktop** (com WSL2 habilitado).
3. (Opcional, mas recomendado) Instale o **PostgreSQL client** (psql) para inspecionar o banco.

## 2) Abrir o projeto no IntelliJ
1. Extraia o zip em uma pasta (ex.: `C:\dev\gym-system`).
2. No IntelliJ: **File → Open…** e selecione a pasta raiz (a que contém `pom.xml`).
3. Aguarde o IntelliJ importar o Maven (ícone do elefante no canto).

### Ajustar o JDK do projeto
- **File → Project Structure → Project**
  - Project SDK: **Java 21**
  - Project language level: **21**
- Em **Settings → Build Tools → Maven**, confirme que o Maven está habilitado.

## 3) Subir o banco de dados (Postgres) via Docker
Abra um terminal na pasta `gym-api` e rode:

```bash
docker compose up -d db
```

Isso sobe apenas o container do Postgres em `localhost:5432` com:
- DB: `gymdb`
- user: `gym`
- pass: `gym`

## 4) Configurar variáveis de ambiente no IntelliJ (Run Config)
1. Abra `GymSystemApplication` e clique em **Run** para o IntelliJ criar a configuração.
2. Vá em **Run → Edit Configurations…**
3. Em **Environment variables**, adicione (exemplo):

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/gymdb;
SPRING_DATASOURCE_USERNAME=gym;
SPRING_DATASOURCE_PASSWORD=gym;
JWT_SECRET=change-me-please-change-me-please-change-me;
JWT_EXP_SECONDS=3600;
BOOTSTRAP_ADMIN_EMAIL=admin@local.test;
BOOTSTRAP_ADMIN_PASSWORD=Admin#12345;
CORS_ALLOWED_ORIGINS=http://localhost:4200;
STORAGE_LOCAL_BASE_PATH=./storage
```

> Dica: você também pode copiar o conteúdo de `.env.example` e colar adaptando.

## 5) Rodar a API
- Execute `GymSystemApplication`.
- Na primeira execução, o **Flyway** cria as tabelas automaticamente.
- Se você configurou `BOOTSTRAP_ADMIN_EMAIL/PASSWORD`, o sistema cria um admin inicial.

## 6) Testar pelo Swagger
Abra:
- http://localhost:8080/swagger-ui.html

### Fluxo mínimo para testar autenticação
1. **Login do admin**: `POST /api/v1/auth/login` com o e-mail/senha do bootstrap.
2. Com o token, clique em **Authorize** (no Swagger) e cole `Bearer <token>`.
3. Crie um token de cadastro: `POST /api/v1/admin/invite-tokens`
4. Faça signup: `POST /api/v1/auth/signup` com `inviteToken` retornado.
5. Use o novo usuário para acessar endpoints de aluno/agenda/booking etc.

## 7) Parar o banco
```bash
docker compose down
```

---

Se algo der erro, veja os logs do Postgres:
```bash
docker logs -f gym-api-db-1
```

e os logs do Spring no console do IntelliJ.
