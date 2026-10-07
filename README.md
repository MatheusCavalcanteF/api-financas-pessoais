# API de Finanças Pessoais

API REST para controle financeiro: transações, categorias, importação de extratos (CSV e OFX) com deduplicação e relatório mensal para dashboard.

Java 25 · Spring Boot 4.1 · PostgreSQL 16 · Flyway · Spring Security + JWT

## Pré-requisitos

- JDK 25
- Docker (banco local e testes com Testcontainers)

O Maven não precisa estar instalado: o projeto usa o wrapper (`mvnw` / `mvnw.cmd`).

## Rodando localmente

**1. Suba o banco**

```bash
docker compose up -d
```

Isso sobe o PostgreSQL 16 em `localhost:5432` com o banco `financas` (usuário e senha `postgres`). As tabelas são criadas pelo Flyway na primeira subida da aplicação.

**2. Compile e rode**

```bash
./mvnw clean package -DskipTests      # Windows: mvnw.cmd clean package -DskipTests
java -jar target/api-0.0.1-SNAPSHOT.jar
```

Para desenvolvimento, `./mvnw spring-boot:run` também funciona.

**3. Acesse**

| O quê | Endereço |
| --- | --- |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| PostgreSQL | localhost:5432 |

No Swagger UI, faça login em `POST /auth/login`, clique em **Authorize** e cole o `accessToken`, sem o prefixo `Bearer`. A partir daí, todas as rotas fora de `/auth` enviam o token.

## Configuração

Todas as variáveis têm um valor padrão para desenvolvimento.

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/financas` | URL JDBC do banco |
| `DB_USERNAME` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | `postgres` | Senha do banco |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `JWT_SECRET` | segredo de dev versionado | Base64, mínimo 256 bits. **Obrigatório fora do ambiente local.** Gere com `openssl rand -base64 64` |
| `JWT_EXPIRATION` | `PT2H` | Validade do token (ISO-8601) |

**A porta 5432 já está em uso?** Mude o mapeamento no `docker-compose.yml` (ex.: `"5433:5432"`) e aponte a aplicação para ela:

```bash
DB_URL=jdbc:postgresql://localhost:5433/financas java -jar target/api-0.0.1-SNAPSHOT.jar
```

## Testes

```bash
./mvnw clean test
```

Os testes de integração sobem um PostgreSQL descartável via Testcontainers, então o Docker precisa estar rodando. Eles não usam o banco do `docker-compose`.

## Testando os fluxos com curl

Os comandos são para bash. No Windows, use o Git Bash ou o WSL. No PowerShell, `curl` é um alias de `Invoke-WebRequest`; chame `curl.exe`.

### 1. Autenticação

Crie o usuário (a senha precisa de no mínimo 8 caracteres):

```bash
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "ana@email.com", "password": "senha-forte-123"}'
```

```json
{"id":"94daa860-...","email":"ana@email.com","role":"USER","createdAt":"2026-10-07T03:58:09Z"}
```

Faça login e guarde o token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "ana@email.com", "password": "senha-forte-123"}' \
  | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
```

A resposta do login é `{"accessToken": "...", "tokenType": "Bearer", "expiresIn": 7200}`. Todas as outras rotas exigem o header `Authorization: Bearer $TOKEN`. O usuário vem sempre do token, nunca de parâmetro ou do corpo da requisição.

### 2. Upload de extrato CSV

```bash
cat > extrato.csv <<'EOF'
date;amount;description;type
2026-10-01;8,00;Café;EXPENSE
2026-10-03;120,50;Mercado;EXPENSE
2026-10-05;5.000,00;Salário;INCOME
EOF

curl -s -X POST http://localhost:8080/transactions/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@extrato.csv"
```

```json
{"totalLines":3,"imported":3,"skippedDuplicates":0}
```

Se você rodar o mesmo upload de novo, nada é duplicado:

```json
{"totalLines":3,"imported":0,"skippedDuplicates":3}
```

Regras da importação:

- **Formato.** É detectado pelo conteúdo do arquivo, não pela extensão. São aceitos CSV e OFX (1.x SGML e 2.x XML). O arquivo pode ter até 5 MB e 10.000 linhas.
- **CSV.** O cabeçalho `date,amount,description,type` pode vir em qualquer ordem. O separador define o formato dos valores:
  - `;` → formato brasileiro (`1.500,50`)
  - `,` → ponto decimal (`1500.50`)
  - Datas em ISO (`2026-10-01`), valores sempre positivos e `type` igual a `INCOME` ou `EXPENSE`.
- **Arquivo inválido.** Qualquer linha inválida rejeita o arquivo inteiro com 400. A resposta lista os erros por linha.
- **Deduplicação.** No OFX, usa o `FITID` do banco junto com a conta. No CSV e no OFX sem `FITID`, usa data + valor + tipo + descrição. Os dois critérios não se cruzam: o mesmo lançamento importado por OFX e depois por CSV aparece duas vezes. Escolha um formato por conta.
- **Categoria.** Vem das regras do usuário (veja abaixo). Sem regra que case, a transação fica sem categoria.

#### Categorização automática

Crie regras de palavra-chave antes de importar. Toda transação nova cuja descrição contenha a keyword recebe a categoria da regra:

```bash
CATEGORY_ID=$(curl -s -X POST http://localhost:8080/categories \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "Alimentação"}' | sed -E 's/.*"id":"([^"]+)".*/\1/')

curl -s -X POST http://localhost:8080/category-rules \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"keyword\": \"café\", \"categoryId\": \"$CATEGORY_ID\"}"
```

- **Comparação.** Ignora maiúsculas, acentos e espaços extras: a keyword `café` casa com `CAFE DA PRAÇA`. A keyword precisa ter no mínimo 3 caracteres.
- **Várias regras casando.** Vence a keyword mais longa: `uber eats` ganha de `uber`.
- **Alcance.** Na importação, as regras valem só para transações novas: uma duplicata ignorada mantém a categoria que já tinha. Para aplicar as regras ao histórico sem categoria, use o endpoint retroativo. Ele nunca troca uma categoria já definida:

  ```bash
  curl -s -X POST http://localhost:8080/category-rules/apply-retroactive \
    -H "Authorization: Bearer $TOKEN"
  # {"updatedCount":15}
  ```
- **Rotas.** O CRUD completo fica em `/category-rules`, com listagem paginada. Cada usuário pode ter até 500 regras.

### 3. Dashboard (relatório mensal)

```bash
curl -s "http://localhost:8080/reports/monthly?year=2026&month=10" \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "year": 2026,
  "month": 10,
  "totalIncome": 5000.00,
  "totalExpense": 128.50,
  "balance": 4871.50,
  "expensesByCategory": [
    {"categoryId": null, "categoryName": "Sem categoria", "total": 128.50}
  ]
}
```

- **Ordenação.** `expensesByCategory` vem do maior para o menor total, e a soma dos totais é sempre `totalExpense`.
- **Despesas sem categoria.** Ficam no grupo `categoryId: null`. Use o `categoryId`, e não o nome, para identificar esse grupo.
- **Mês sem lançamentos.** Retorna os totais zerados e a lista vazia.
- **Parâmetros inválidos.** Ano fora de 1900–9999 ou mês fora de 1–12 retornam 400 no formato `ProblemDetail` (RFC 7807).

## Estrutura

Os pacotes são organizados por domínio, em `src/main/java/com/finance/api`:

| Pacote | Responsabilidade |
| --- | --- |
| `auth` | Registro e login |
| `security` | Filtro JWT e configuração do Spring Security |
| `user` | Entidade de usuário |
| `category` | CRUD de categorias e regras de categorização automática (`category.rule`) |
| `transaction` | CRUD, listagem paginada e importação CSV/OFX |
| `report` | Relatório mensal |
| `shared` | Tratamento de erros (`ProblemDetail`) e validações |

As migrations ficam em `src/main/resources/db/migration`.
