# Ligar Wellhub e TotalPass em produção

O código dos dois está pronto e testado contra a documentação oficial. O que falta é **credencial**, e ela vem de processo comercial — não dá para gerar sozinho.

## Quem pede o quê

A credencial é dividida em duas metades, e isso vem da documentação dos dois parceiros:

| | Da **Crivo** (integradora, uma vez) | De **cada academia** cliente |
|---|---|---|
| **Wellhub** | Bearer token | Gym ID da unidade (`X-Gym-Id`) |
| **TotalPass** | `partner_api_key` | `place_api_key` |

O token do Wellhub *"identifica você e as academias que você tem permissão de validar"* — é um só, e o `X-Gym-Id` escolhe a unidade. A `partner_api_key` da TotalPass é do ERP e, nas palavras deles, **"nunca deve ser solicitada aos seus clientes"**; a `place_api_key` a própria academia gera no portal.

**Na prática:** as credenciais da Crivo vão em variável de ambiente. As de cada academia são cadastradas no painel, em **Parceiros → Credenciais desta academia**, e ficam no banco por tenant.

Este guia é a sequência do começo ao fim.

---

## 1. Pedir as credenciais

### Wellhub (antigo Gympass)

A Access Control API é liberada pelo time comercial. Peça pelo canal de parceiros ou pelo gerente da conta da academia:

- **Bearer token de autenticação** (serve para Access Control e Booking)
- **Gym ID** de cada unidade que você vai validar
- **Acesso ao sandbox** — `apitesting.partners.gympass.com`

> Peça o sandbox junto. É o único dos dois parceiros que oferece ambiente de teste, e ele permite validar o fluxo inteiro antes de tocar em produção.

Documentação: <https://developers.wellhub.com/product/access-control-api/1.0/getting-started>

### TotalPass

Mande um e-mail para **tp.integracoes@totalpass.com.br** com:

- Nome do ERP (aqui: GymSystem)
- CNPJ
- Representante legal

Eles devolvem o **`partner_api_key`** (confidencial, do sistema — nunca peça ao cliente).
A **`place_api_key`** é a própria academia que gera, no portal TotalPass, aba **Integrações**.

Documentação: <https://dev.totalpass.com/docs/>

---

## 2. Preencher o `.env`

Só as credenciais da Crivo:

```bash
CHECKIN_WEBHOOK_SECRET=  # troque: é o que protege a URL do webhook

WELLHUB_MODE=live
WELLHUB_BASE_URL=https://apitesting.partners.gympass.com/access/v1   # sandbox primeiro
WELLHUB_TOKEN=

TOTALPASS_MODE=live
TOTALPASS_PARTNER_API_KEY=
```

Em produção, troque `WELLHUB_BASE_URL` para `https://api.partners.gympass.com/access/v1`.

## 2.1. Cadastrar as credenciais de cada academia

No painel, **Parceiros → Credenciais desta academia**:

- **Wellhub · Gym ID** — o id daquela unidade na sua lista de academias
- **TotalPass · place_api_key** — a chave que a academia gerou no portal dela

A `place_api_key` é guardada como segredo: o servidor devolve só os últimos caracteres, o suficiente para conferir que é a chave certa. Sem esse cadastro, o diagnóstico acusa "Falta configurar" para aquela academia — e nenhuma outra é afetada.

---

## 3. Conferir a conexão antes de abrir a recepção

Suba a API e abra **Parceiros** no painel. O bloco "Conexão com os parceiros" testa credencial e alcance **sem liberar entrada nenhuma**:

| O que aparece | O que significa |
|---|---|
| Modo teste | Ainda em `mock`; nada foi verificado contra o parceiro |
| Falta configurar | Diz o que falta, e de quem: variável da Crivo ou credencial desta academia |
| Inacessível | Não chegou no servidor do parceiro (rede, URL, firewall) |
| Credencial recusada | Chegou, mas a chave foi rejeitada |
| Conectado | O parceiro respondeu e aceitou a credencial |

Pela API: `GET /api/v1/admin/partners/diagnostics`

---

## 4. TotalPass: registrar o webhook

A TotalPass precisa alcançar a academia pela internet.

1. Em **Parceiros**, copie a URL mostrada (ela carrega a academia e o segredo — trate como credencial)
2. Clique em **Registrar na TotalPass**, ou cadastre a URL manualmente no portal deles
3. Em desenvolvimento, use um túnel (ngrok e similares) e ajuste `APP_BASE_URL`

Sem isso, os check-ins do TotalPass simplesmente não chegam.

---

## 5. Cadastrar a identidade dos alunos

Os dois parceiros identificam a pessoa por um id do lado deles. Sem o vínculo, a entrada não tem a quem ser atribuída.

- **Wellhub**: o `gympass_id` de 13 dígitos, que o aluno vê no canto superior esquerdo do app. Cadastre em **Parceiros → Vincular aluno**.
- **TotalPass**: o CPF, que vem no webhook. Na primeira visita o vínculo é criado sozinho — a recepção informa o e-mail do aluno ao liberar a entrada, e da próxima vez ele já é reconhecido.

O campo **PIN/QR** do Wellhub é opcional: é o código interno da academia (catraca, biometria) que passa a valer também para o aluno do Wellhub.

---

## 6. Operação no dia a dia

**Wellhub** — o aluno faz check-in no app dele e a recepção confirma pela tela de Check-in. A academia consulta; a resposta é imediata.

**TotalPass** — o aluno faz check-in no app e a entrada aparece sozinha em **Parceiros**, na fila de "Entradas aguardando liberação". A recepção clica em Liberar. **O prazo é de 90 minutos**; passou disso, o parceiro não aceita mais.

Se a recepção validar pelo portal da TotalPass antes do sistema, a tentativa aqui volta com "já validado no portal" — a integração não bloqueia o portal de propósito. Combine com a academia qual será o fluxo oficial.

---

## O que conferir se algo não funcionar

| Sintoma | Onde olhar |
|---|---|
| "Aluno sem Wellhub ID cadastrado" | Falta o vínculo do aluno em Parceiros |
| "Academia sem Gym ID do Wellhub cadastrado" | Falta a credencial da academia em Parceiros |
| "Nenhum check-in ativo no Wellhub" | O aluno não fez check-in no app, ou já expirou |
| Fila do TotalPass sempre vazia | Webhook não registrado, URL não é pública, ou `CHECKIN_WEBHOOK_SECRET` diferente do que está na URL |
| "Check-in já validado no portal" | A recepção validou manualmente antes |
| Entradas expirando sozinhas | Ninguém liberou dentro dos 90 minutos |

---

## Uma ressalva honesta

O cliente foi implementado conforme a documentação pública dos dois parceiros e verificado em HTTP real contra um servidor que reproduz esses contratos — autenticação, headers, corpo, parsing e códigos de erro.

Isso prova que o cliente está correto **em relação à documentação**. Não prova que o ambiente real do parceiro se comporta exatamente como ela descreve. Use o sandbox do Wellhub antes de produção, e trate o primeiro dia de TotalPass como piloto, com a recepção sabendo validar pelo portal se precisar.
