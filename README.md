# API de Finanças Pessoais

API REST para controle financeiro: transações, categorias, importação de extratos (CSV e OFX) com deduplicação e relatório mensal para dashboard.

Java 25 · Spring Boot 4.1 · PostgreSQL 16 · Flyway · Spring Security + JWT

## Pré-requisitos

- JDK 25
- Docker (banco local e testes com Testcontainers)

O Maven não precisa estar instalado: o projeto usa o wrapper (`mvnw` / `mvnw.cmd`).

## Rodando localmente

**1. Crie o `.env`**

```bash
cp .env.example .env
```

Preencha `DB_PASSWORD` (qualquer senha) e `JWT_SECRET` (gere com `openssl rand -base64 48`). O `.env` está no `.gitignore` e é lido tanto pelo `docker compose` quanto pela aplicação.

**2. Suba o banco**

```bash
docker compose up -d
```

Isso sobe o PostgreSQL 16 em `localhost:5432` (acessível só pela própria máquina) com o banco `financas`, usuário `postgres` e a senha do `DB_PASSWORD`. As tabelas são criadas pelo Flyway na primeira subida da aplicação.

**3. Compile e rode**

```bash
./mvnw clean package -DskipTests      # Windows: mvnw.cmd clean package -DskipTests
java -jar target/api-0.0.1-SNAPSHOT.jar
```

Para desenvolvimento, `./mvnw spring-boot:run` também funciona. Rode os dois a partir da raiz do projeto: é de lá que a aplicação lê o `.env`.

**4. Acesse**

| O quê | Endereço |
| --- | --- |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| PostgreSQL | localhost:5432 |

O Swagger UI e o `/v3/api-docs` só existem com `SWAGGER_ENABLED=true`, que já vem no `.env.example`. Sem a variável (ou com `false`), as duas rotas respondem 404. Fora de desenvolvimento, deixe a variável ausente ou `false`.

No Swagger UI, faça login em `POST /auth/login`, clique em **Authorize** e cole o `accessToken`, sem o prefixo `Bearer`. A partir daí, todas as rotas fora de `/auth` enviam o token.

## Configuração

As variáveis podem vir do `.env` ou do ambiente; o ambiente tem precedência. `DB_PASSWORD` e `JWT_SECRET` não têm valor padrão: sem elas, o banco não sobe e a aplicação também não.

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/financas` | URL JDBC do banco |
| `DB_USERNAME` | `postgres` | Usuário do banco |
| `DB_PASSWORD` | — | Senha do banco. **Obrigatória.** |
| `SERVER_PORT` | `8080` | Porta HTTP |
| `JWT_SECRET` | — | Base64, mínimo 256 bits. **Obrigatório.** Gere com `openssl rand -base64 48` |
| `JWT_EXPIRATION` | `PT2H` | Validade do token (ISO-8601) |
| `SWAGGER_ENABLED` | `false` | Liga o Swagger UI e o `/v3/api-docs`. Só em desenvolvimento |
| `TRUSTED_PROXIES` | vazio | IPs/CIDRs separados por vírgula dos proxies cujo `X-Forwarded-For` é confiável. Veja [Rate limit](#rate-limit) |

**A porta 5432 já está em uso?** Mude o mapeamento no `docker-compose.yml` (ex.: `"127.0.0.1:5433:5432"`) e aponte a aplicação para ela:

```bash
DB_URL=jdbc:postgresql://localhost:5433/financas java -jar target/api-0.0.1-SNAPSHOT.jar
```

## Rate limit

`POST /auth/login` e `POST /auth/register` têm limite de tentativas por IP:

| Regra | Padrão |
| --- | --- |
| Falhas de login seguidas do mesmo e-mail + IP | 5 bloqueiam o par por 15 minutos. Um login com sucesso zera o contador |
| Tentativas de login por IP, qualquer e-mail | 20 por minuto |
| Tentativas de cadastro por IP | 5 por hora |

- **Resposta.** Estourou, a API responde `429` com `ProblemDetail` (título "Muitas tentativas") e o header `Retry-After` em segundos. Durante o bloqueio a senha nem é conferida, e a resposta é a mesma para e-mail cadastrado ou não.
- **Janela.** Os limites por IP usam janela deslizante: "20 por minuto" vale para qualquer intervalo de 60 segundos, não para o minuto do relógio.
- **Configuração.** Os números ficam em `security.rate-limit` no `application.yml`. Os contadores ficam em memória e expiram sozinhos, com teto de `max-tracked-keys` chaves por contador. Por isso cada instância da API conta separado e um restart zera tudo.

### IP do cliente e `TRUSTED_PROXIES`

Por padrão, o IP é o da conexão TCP (`getRemoteAddr()`), e os headers `X-Forwarded-For`, `Forwarded` e `X-Real-IP` são ignorados, porque qualquer cliente pode escrevê-los.

Quando a conexão vem de um IP listado em `TRUSTED_PROXIES`, a API lê o `X-Forwarded-For` da direita para a esquerda e usa o primeiro endereço que não é de proxy confiável. O que estiver à esquerda dele foi escrito pelo cliente e não vale. `server.forward-headers-strategy` fica desligado de propósito: ele confiaria no header vindo de qualquer IP de rede interna.

Isso só funciona se algum proxy do caminho **acrescentar** o IP real ao `X-Forwarded-For`. O proxy do Next (Route Handler `/api/[...path]` do frontend) não acrescenta: quando o cliente não manda o header, o Next o preenche com o IP da conexão; quando manda, o Next mantém o valor enviado pelo cliente, sem acrescentar nada. Como esse valor pode ter sido escrito pelo cliente, o header só é confiável com um proxy na frente do Next que acrescente o IP. Na prática:

- **Navegador → Next → API, sem nada na frente do Next.** Deixe `TRUSTED_PROXIES` vazio. Todos os usuários chegam com o IP do Next, então os limites por IP viram limites globais (20 logins por minuto e 5 cadastros por hora para o sistema inteiro), e 5 senhas erradas bloqueiam aquele e-mail para todo mundo. Se você colocar o IP do Next em `TRUSTED_PROXIES` nesse cenário, o cliente escolhe o próprio IP pelo header.
- **Navegador → proxy reverso → Next → API.** Use um proxy que acrescente o IP (nginx com `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for`, ou um load balancer) e liste em `TRUSTED_PROXIES` o IP do Next e de qualquer outro proxy entre ele e a API. O Next também não pode ser acessível sem passar por esse proxy.

Prefira IPs exatos a faixas largas como `10.0.0.0/8`: qualquer coisa que abra conexão a partir de um IP da lista pode escolher o IP que a API vai enxergar.

### Em produção, a API não pode ficar acessível direto

A porta da API (8080) só deve aceitar conexões do Next ou do proxy, nunca da internet: rede interna do Docker sem `ports:` publicado, firewall ou security group. A confiança no `X-Forwarded-For` depende de quem consegue chegar à API vindo de um IP de `TRUSTED_PROXIES`. Um exemplo de como isso falha: com a porta publicada no host, o Docker pode trocar o IP de origem de quem vem de fora pelo IP do gateway da rede (ex.: `172.17.0.1`). Se esse IP estiver dentro de um CIDR de `TRUSTED_PROXIES`, qualquer pessoa na internet escolhe o próprio IP pelo header.

No cenário com proxy reverso, o mesmo vale para o Next: ele só pode ser acessado pelo proxy. Quem chega no Next sem passar pelo proxy escolhe o IP que a API enxerga, porque o Next mantém o `X-Forwarded-For` que o cliente mandou (só o preenche com o IP da conexão quando o header não vem) e a API confia no Next.

## Testes

```bash
./mvnw clean test
```

Os testes de integração sobem um PostgreSQL descartável via Testcontainers, então o Docker precisa estar rodando. Eles não usam o banco do `docker-compose` e não dependem do `.env`: o segredo JWT de teste fica em `src/test/resources/application.properties`.

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

Errou a senha 5 vezes seguidas? O login desse e-mail a partir do seu IP fica bloqueado por 15 minutos e responde `429` (veja [Rate limit](#rate-limit)).

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
| `security` | Filtro JWT, configuração do Spring Security e rate limit (`security.ratelimit`) |
| `user` | Entidade de usuário |
| `category` | CRUD de categorias e regras de categorização automática (`category.rule`) |
| `transaction` | CRUD, listagem paginada e importação CSV/OFX |
| `report` | Relatório mensal |
| `shared` | Tratamento de erros (`ProblemDetail`) e validações |

As migrations ficam em `src/main/resources/db/migration`.
