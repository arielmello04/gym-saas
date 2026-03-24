# 🏋️‍♂️ GymSystem API

Sistema de gestão de academias desenvolvido em **Java com Spring Boot**, com arquitetura modular e endpoints REST.  
Atualmente o projeto contempla apenas o **backend**, mas em breve será desenvolvido o **frontend** em Angular.

---

## 🚀 Visão Geral

O **GymSystem** é uma API voltada para academias que desejam automatizar cadastros, planos, check-ins e controle financeiro.  
Foi estruturado para permitir expansão futura (como portal do aluno, dashboards e relatórios), seguindo princípios de **Clean Architecture** e **boas práticas REST**.

---

## 🏗️ Tecnologias Utilizadas

| Categoria | Tecnologias |
|------------|--------------|
| **Linguagem** | Java 17 |
| **Framework** | Spring Boot 3 (Web, Security, Data JPA, Validation) |
| **Banco de Dados** | PostgreSQL |
| **ORM** | Hibernate / JPA |
| **Documentação da API** | Springdoc OpenAPI / Swagger UI |
| **Testes** | JUnit 5, Mockito |
| **Build e Dependências** | Maven |
| **Containerização (futuro)** | Docker e Docker Compose |

---

## 📘 Documentação da API

A documentação é gerada automaticamente pelo **Springdoc OpenAPI**.  
Após iniciar o projeto, acesse:

🔗 **http://localhost:8080/swagger-ui.html**

Lá você encontrará todos os endpoints, parâmetros, modelos de requisição e exemplos de resposta.

---

## 🧪 Testes

Para executar os testes:
```bash
mvn test
```

Os testes unitários e de integração estão em `src/test/java/`, cobrindo:
- Controllers
- Services
- Repositories
- Fluxos de autenticação e validação

---

## 🧱 Próximos Passos

- [ ] Início do **frontend** em **Angular 17+**
- [ ] Implementação de **autenticação JWT**
- [ ] Upload de arquivos (exames, contratos, fotos)
- [ ] Integração com **gateways de pagamento**
- [ ] **Dockerização** completa (backend + banco)
- [ ] Deploy em **AWS / Render / Railway**

---

## 🧠 Arquitetura e Boas Práticas

- **Arquitetura em camadas:** Controller → Service → Repository  
- **DTOs** para comunicação entre camadas e isolamento de entidades JPA  
- **Tratamento global de erros** com `@ControllerAdvice`  
- **Validação de campos** com `@Valid` e `javax.validation`  
- **Mapeamento JPA** com anotações (`@Entity`, `@OneToMany`, `@JoinColumn`)  
- **Versionamento de API** planejado para `/api/v1/...`  
- **Swagger** configurado para documentação automática  

---

## 📦 Exemplos de Endpoints

### 🔹 GET — Listar alunos
```
GET /api/v1/alunos
```

### 🔹 POST — Cadastrar novo aluno
```
POST /api/v1/alunos
Content-Type: application/json

{
  "nome": "João Silva",
  "email": "joao@example.com",
  "plano": "Mensal",
  "dataInicio": "2025-01-10"
}
```

### 🔹 DELETE — Remover aluno
```
DELETE /api/v1/alunos/{id}
```

---

## 👨‍💻 Autor

**Ariel Melo (Full Stack Developer)**  
📧 [ariel.melo2001@gmail.com](mailto:ariel.melo2001@gmail.com)  
🌐 [linkedin.com/in/arielmello04](https://linkedin.com/in/arielmello04)  
💻 *"Código limpo, escalável e com propósito."*

---

## 📝 Licença

Este projeto está licenciado sob a **MIT License** — veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

> 💬 *Em breve: frontend em Angular integrado à API, com autenticação JWT, dashboards e design responsivo!*
