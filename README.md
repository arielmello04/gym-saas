# Gym System

<p align="center">
  <b>Sistema de gestão para academias</b><br>
  Backend em <b>Spring Boot</b> e frontend em <b>Angular</b>
</p>

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-17+-informational">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-Backend-success">
  <img alt="Angular" src="https://img.shields.io/badge/Angular-Frontend-red">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-Database-blue">
  <img alt="JWT" src="https://img.shields.io/badge/Auth-JWT-orange">
</p>

## Sobre o projeto

O **Gym System** é uma aplicação full stack voltada para a administração de academias.  
O projeto foi desenvolvido com foco em organização, autenticação, controle de usuários e rotinas comuns do ambiente de academia, como planos, pagamentos, check-ins e agendamentos.

Além do uso prático, o sistema também foi construído como projeto de estudo e portfólio, aplicando conceitos reais de arquitetura backend, integração com frontend e boas práticas de desenvolvimento.

## Funcionalidades

- Cadastro e login de usuários
- Autenticação com JWT
- Controle de perfis e permissões
- Gerenciamento de membros
- Gerenciamento de planos
- Controle de pagamentos
- Check-in de alunos
- Agendamentos
- Estrutura preparada para multi-tenant

## Tecnologias utilizadas

### Backend
- Java
- Spring Boot
- Spring Security
- JWT
- JPA / Hibernate
- Flyway
- Maven

### Frontend
- Angular
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

- Java 17+
- Maven
- Node.js
- Angular CLI
- PostgreSQL

## Backend

Entre na pasta do backend:

```bash
cd gym-api
```

Execute o projeto:

```bash
./mvnw spring-boot:run
```

Ou, se estiver usando Maven instalado:

```bash
mvn spring-boot:run
```

## Frontend

Entre na pasta do frontend:

```bash
cd gym-angular
```

Instale as dependências:

```bash
npm install
```

Execute o projeto:

```bash
ng serve
```

A aplicação estará disponível em:

```bash
http://localhost:4200
```

## Configuração

Antes de executar, ajuste os arquivos de configuração conforme seu ambiente, principalmente:

- credenciais do banco de dados no backend
- URL da API no frontend
- variáveis de ambiente, se necessário

## Objetivo do projeto

Este projeto foi criado com foco em:

- prática de desenvolvimento full stack
- construção de portfólio
- aplicação de autenticação e autorização
- organização em camadas no backend
- integração entre API REST e frontend moderno

## Melhorias futuras

- upload e gerenciamento de documentos
- testes automatizados
- melhorias de UX/UI
- integração com serviços externos

## Autor

Desenvolvido por **Ariel Melo**.
