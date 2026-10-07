# Finanças Pessoais — Frontend

Interface web da API de finanças pessoais (Spring Boot, na raiz deste repositório): dashboard mensal, importação de extratos OFX/CSV, transações, categorias e regras de categorização automática.

**Stack:** Next.js 16 (App Router) · React 19 · TypeScript · Tailwind CSS v4 · Lucide React · Axios

## Pré-requisitos

- Node.js **20.9 ou superior**
- O backend rodando (por padrão em `http://localhost:8080`, com o PostgreSQL dele no ar)

## Como rodar

Todos os comandos abaixo rodam dentro da pasta `frontend/`.

```bash
# 1. Dependências
cd frontend
npm install

# 2. Variáveis de ambiente (opcional se o backend estiver em localhost:8080)
cp .env.example .env.local

# 3. Servidor de desenvolvimento
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000), crie uma conta na aba **Cadastrar** e pronto.

## Variáveis de ambiente

Crie o arquivo `.env.local` na raiz do projeto (ele não vai para o git):

```bash
# Endereço do backend Spring Boot
BACKEND_URL=http://localhost:8080
```

| Variável | Padrão | Para quê |
| --- | --- | --- |
| `BACKEND_URL` | `http://localhost:8080` | Para onde o Next encaminha as chamadas `/api/*` |

> O valor é lido quando o servidor sobe e fica gravado no build. Depois de alterar, reinicie o `npm run dev` ou rode `npm run build` de novo.

A variável não tem o prefixo `NEXT_PUBLIC_` de propósito: o navegador nunca fala direto com o backend.

## Como o frontend fala com a API

- O navegador chama `/api/...` na própria origem e o Next repassa para o `BACKEND_URL` (rewrite em `next.config.ts`). Por isso o backend não precisa de CORS.
- Após o login, o JWT fica no `localStorage` e todas as chamadas (menos `/auth/login` e `/auth/register`) levam `Authorization: Bearer <token>` automaticamente.
- Se o token expirar (401), o usuário volta para `/login` e, depois de entrar, retorna à página onde estava.

## Scripts

| Comando | O que faz |
| --- | --- |
| `npm run dev` | Servidor de desenvolvimento em `localhost:3000` |
| `npm run build` | Build de produção (inclui checagem de tipos) |
| `npm run start` | Sobe o build de produção |
| `npm run lint` | ESLint |
| `npm run test:e2e` | Suíte E2E com Playwright (ver abaixo) |

## Testes E2E

```bash
npm run test:e2e
```

A suíte (`e2e/`) cobre login, cadastro, sessão expirada, proteção contra open redirect, dashboard, importação de extratos, paginação, categorias, regras e a lixeira em duas etapas.

- **Não precisa do backend real:** sobe um backend falso em memória (`e2e/mock-backend.mjs`, porta 18080) que imita o contrato do Spring Boot, com os mesmos erros 401/404/409 e a mesma paginação.
- **Não atrapalha o desenvolvimento:** o app de teste é um build de produção em `.next-e2e`, na porta 3100. Pode rodar com o `npm run dev` aberto.
- **Usa o Google Chrome instalado** na máquina, então não baixa navegador. Sem o Chrome, rode `npx playwright install chromium` e remova `channel: "chrome"` do `playwright.config.ts`.
- Requisições para fora de `localhost` são bloqueadas durante os testes.

Mudou um contrato no backend? Atualize `src/types`, o `e2e/mock-backend.mjs` e os testes juntos.

## Estrutura

```
e2e/                  # Testes Playwright + backend falso (mock-backend.mjs)
src/
  app/
    login/            # Entrar / Cadastrar
    (app)/            # Área logada (guarda + navegação)
      dashboard/      # Resumo mensal e despesas por categoria
      transactions/   # Importação de extratos e lista paginada
      settings/       # Categorias e regras de categorização
  components/         # Componentes de UI por tela
  hooks/              # Busca de dados (useApiQuery e derivados)
  lib/                # api.ts (Axios + endpoints), auth.ts (sessão), format.ts
  types/              # Tipos espelhando os DTOs do backend
```

## Teste integrado com o backend real

Para usar o app de ponta a ponta com o Spring Boot e o PostgreSQL de verdade, rode os dois projetos em paralelo, cada um no seu terminal.

**Terminal 1: backend** (raiz do repositório, requer JDK 25 e Docker)

```bash
docker compose up -d        # PostgreSQL 16 em localhost:5432
./mvnw spring-boot:run      # API em http://localhost:8080 (Windows: mvnw.cmd spring-boot:run)
```

**Terminal 2: frontend** (pasta `frontend/`)

```bash
npm run dev                 # http://localhost:3000
```

Para conferir se o proxy alcança o backend, rode `curl http://localhost:3000/api/v3/api-docs`, que deve devolver o JSON do OpenAPI. Depois, crie uma conta em `/login`, importe um extrato CSV ou OFX em **Transações** e veja o resumo no **Dashboard**.

Se o backend estiver em outra porta, ajuste o `BACKEND_URL` no `.env.local` e reinicie o `npm run dev`.

Convenções do projeto para quem for contribuir estão em [`CONTRIBUTING.md`](./CONTRIBUTING.md).
