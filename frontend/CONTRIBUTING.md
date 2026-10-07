# financas-frontend

Frontend de finanças pessoais que consome a API `api-financas-pessoais` (Spring Boot).

## Stack

- Next.js 16 (App Router) + React 19 + TypeScript (strict)
- Tailwind CSS v4
- Lucide React para ícones (`import { Wallet } from "lucide-react"`)
- Axios para consumo da API, sempre via `src/lib/api.ts`

Alias de import: `@/*` -> `src/*`.

## Backend

- URL: `http://localhost:8080`, acessado **só via proxy do Next**: `/api/:path*` -> backend (`rewrites` em `next.config.ts`). A `baseURL` do axios é `/api`; não há CORS no backend, então nunca chame `:8080` direto do browser. Outro host: defina `BACKEND_URL` (env do servidor).
- Autenticação stateless por JWT (Bearer), validade padrão de 2h, sem refresh token
- Swagger: `http://localhost:8080/swagger-ui.html`

## Regra de ouro: autenticação

Toda chamada à API, exceto `POST /auth/login` e `POST /auth/register`, leva `Authorization: Bearer <token>`.

- Use **sempre** a instância `api` de `@/lib/api`. Nunca `fetch` nem `axios` direto: o interceptor é quem injeta o token.
- Sessão em `@/lib/auth` (token no `localStorage`, decodificação, redirects). Nos componentes, use `useAuth()` (`status`, `user`, `signIn`, `signUp`, `signOut`) de `@/components/auth-provider`.
- Claims do JWT: `sub` é o **UUID** do usuário; o e-mail está em `email`; também há `role` e `exp`. `decodeToken` não valida assinatura: serve só para exibir dados, nunca para decidir permissão.
- Um 401 em rota protegida limpa o token e redireciona para `/login?from=<rota>`. Um 401 em `/auth/login` é senha errada e volta como erro normal para o formulário.
- Todo redirect para login passa por `redirectToLogin()`, e todo redirect pós-login passa por `safeRedirectPath()` (bloqueia open redirect). Não monte esses redirects à mão.
- Rotas autenticadas ficam no route group `src/app/(app)/` (`/dashboard`, `/transactions`, `/settings`). O `(app)/layout.tsx` faz a guarda (só UX; quem protege os dados é o backend) e renderiza a navegação (`AppNav`). Página nova da área logada = pasta dentro de `(app)/` + item em `NAV_ITEMS` (`src/components/app-nav.tsx`).
- Como o token está no `localStorage`, chamadas autenticadas só funcionam em **Client Components** (`"use client"`). Server Components não têm acesso ao token.
- Para ler query string em Client Components, `useSearchParams` exige `<Suspense>` (projeto usa `cacheComponents`). Em handlers/efeitos, `window.location.search` basta.

## Busca de dados

- Cada endpoint ganha uma função tipada em `src/lib/api.ts` (ex.: `getMonthlyReport`) que aceita `{ signal }`.
- Leituras: hook em `src/hooks/` sobre `useApiQuery(key, fetcher)` (ex.: `useTransactions`, `useMonthlyReport`). A `key` deve conter tudo de que o fetcher depende; trocar a key cancela a requisição anterior e mantém os dados antigos visíveis (`loading` = desatualizado). `reload()` força nova busca com a mesma key.
- Escritas (import, recategorizar): chamada direta à função de `api.ts` no handler, com estado local de "enviando" e feedback na própria tela; ao concluir, o componente avisa o pai (`onDataChanged`) para recarregar as listas.
- Erros: `getErrorMessage(error)` para a mensagem; `getErrorList(error)` quando o backend manda `errors` como lista (linhas inválidas da importação).
- Datas "de hoje" (`new Date()`) só no cliente, em inicializador lazy de `useState`: a página é prerenderizada no build.

## Contratos da API

Tipos em `src/types/index.ts`, espelhando os records Java. Mudou um DTO no backend? Atualize o tipo.

- Valores (`BigDecimal`) chegam como `number`. `amount` é sempre positivo; o sinal vem de `type` (`INCOME` | `EXPENSE`). Exiba sempre com `formatCurrency` (`@/lib/format`, `Intl.NumberFormat` pt-BR/BRL) e não some valores no cliente: os totais vêm prontos do backend.
- Datas: `date` é `yyyy-MM-dd` (LocalDate, sem fuso); `createdAt` é ISO-8601 UTC.
- Listas paginadas (`/transactions`, `/category-rules`) vêm como `Page<T>`: `{ content, page: { size, number, totalElements, totalPages } }`. Página começa em 0, `size` máximo 100. Ordenação: `?sort=date,desc`.
- `/categories` não é paginado (retorna array). `/category-rules` é paginado, mas o limite é 500 regras por usuário: `listAllCategoryRules` busca todas as páginas (size 100).
- `DELETE /categories/{id}`: 409 se houver transações vinculadas; sem transações, o banco apaga as **regras** da categoria em cascata. Quem exclui categoria precisa recarregar também as regras.
- 409 também em nome de categoria duplicado (ignora maiúsculas) e keyword de regra duplicada (ignora maiúsculas e acentos). Para mensagens específicas por status, use `getErrorStatus(error)`.
- Erros vêm como `ApiError` (ProblemDetail): use `detail` para a mensagem e `errors` (campo -> mensagem) em erros de validação 400.

| Método | Rota | Resposta |
| --- | --- | --- |
| POST | `/auth/register` | `User` (201) |
| POST | `/auth/login` | `TokenResponse` |
| GET/POST | `/transactions` | `Page<Transaction>` / `Transaction` |
| GET/PUT/DELETE | `/transactions/{id}` | `Transaction` / 204 |
| POST | `/transactions/import` | `ImportSummaryResponse` (multipart, campo `file`, OFX ou CSV, até 5MB) |
| GET/POST | `/categories` | `Category[]` / `Category` |
| GET/PUT/DELETE | `/categories/{id}` | `Category` / 204 |
| GET/POST | `/category-rules` | `Page<CategoryRule>` / `CategoryRule` |
| GET/PUT/DELETE | `/category-rules/{id}` | `CategoryRule` / 204 |
| POST | `/category-rules/apply-retroactive` | `RetroactiveCategorizationResponse` |
| GET | `/reports/monthly?year=&month=` | `MonthlyReportResponse` |

Não existe endpoint `/me`: o usuário logado não pode ser consultado depois do login.

## Comandos

```bash
npm run dev       # http://localhost:3000
npm run build
npm run lint
npm run test:e2e  # Playwright + backend falso; rode antes de commitar mudanças de UI ou de contrato
```

## Testes E2E

- Specs em `e2e/*.spec.ts`; importe `test`/`expect` de `./helpers` (não de `@playwright/test`): as fixtures automáticas reiniciam o mock a cada teste e bloqueiam rede externa.
- `e2e/mock-backend.mjs` espelha o contrato do backend. Mudou um DTO, status de erro ou paginação no Spring? Atualize `src/types`, o mock e os testes juntos.
- O app de teste roda como build de produção em `.next-e2e` (`NEXT_DIST_DIR`), porta 3100; o mock usa a 18080. Não conflita com o `npm run dev`.
- Prefira seletores acessíveis (`getByRole`, `getByLabel`) e escopo por card/form: o anunciador de rotas do Next também tem `role="alert"`.
