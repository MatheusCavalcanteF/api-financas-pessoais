# financas-frontend

Frontend de finanças pessoais que consome a API `api-financas-pessoais` (Spring Boot).

## Stack

- Next.js 16 (App Router) + React 19 + TypeScript (strict)
- Tailwind CSS v4
- Lucide React para ícones (`import { Wallet } from "lucide-react"`)
- Axios para consumo da API, sempre via `src/lib/api.ts`

Alias de import: `@/*` -> `src/*`.

## Backend

- URL: `http://localhost:8080`, acessado **só pelos Route Handlers do Next** em `src/app/api` (não há rewrite). A `baseURL` do axios é `/api`; não há CORS no backend, então nunca chame `:8080` direto do browser. Outro host: defina `BACKEND_URL` (env do servidor, nunca `NEXT_PUBLIC_`).
- `/api/[...path]` é o proxy: só repassa recursos de `BACKEND_RESOURCES` (`transactions`, `categories`, `category-rules`, `reports`) e o `auth/register` exato; o resto dá 404 sem chegar ao backend. **Recurso novo no backend = entrada nova em `BACKEND_RESOURCES`.** Também filtra headers (lista fixa nos dois sentidos), bloqueia path com escape/`;`/`..` e limita o corpo (multipart 5 MB + folga, demais 1 MB).
- `APP_ORIGIN` (env do servidor, obrigatória em produção): única origem aceita no header `Origin` de mutações em `/api`. Sem ela em produção, todo POST/PUT/PATCH/DELETE recebe 403.
- Autenticação stateless por JWT, validade padrão de 2h, sem refresh token
- Swagger: `http://localhost:8080/swagger-ui.html` (direto no backend; não passa pelo proxy)

## Regra de ouro: autenticação

**O JWT nunca fica acessível ao JavaScript do navegador.** Ele vive num cookie `HttpOnly; SameSite=Strict` (`__Host-financas_session` com `Secure` em produção, `financas_session` no dev) que só os Route Handlers leem e gravam (`src/lib/server/session.ts`). Nada de `localStorage`, `sessionStorage` ou cookie legível para o token, e nenhuma resposta de `/api` pode trazer o token no corpo.

- `POST /api/auth/login` chama o backend pelo servidor, grava o cookie e devolve só `SessionUser` (`id`, `email`, `role`, `expiresAt`). `GET /api/auth/session` diz quem está logado; `POST /api/auth/logout` apaga o cookie. O `/api/[...path]` lê o cookie e injeta `Authorization: Bearer`.
- Use **sempre** a instância `api` de `@/lib/api`. A única exceção é o `fetch` do logout dentro de `redirectToLogin` (`@/lib/auth`): usar `api` ali criaria ciclo de import e dispararia o interceptor de 401.
- Código de servidor fica em `src/lib/server/` com `import "server-only"`. Todo Route Handler novo em `/api` começa com `rejectCrossSite(request)` (`src/lib/server/csrf.ts`): recusa `Sec-Fetch-Site: cross-site` e, fora de GET/HEAD, exige `Origin` igual a `APP_ORIGIN`. Respostas de `/api` saem com `Cache-Control: no-store`.
- Sessão no cliente: use `useAuth()` (`status`, `user`, `signIn`, `signUp`, `signOut`) de `@/components/auth-provider`. Login e logout avisam as outras abas por `BroadcastChannel` (`notifyAuthChange` em `@/lib/auth`).
- Claims do JWT: `sub` é o **UUID** do usuário; o e-mail está em `email`; também há `role` e `exp`. `decodeSessionToken` (servidor) não valida assinatura: serve só para exibir dados, nunca para decidir permissão.
- Um 401 do backend em rota protegida: o `/api/[...path]` apaga o cookie na própria resposta, e o interceptor chama `redirectToLogin`, que faz o logout e vai para `/login?from=<rota>`. Um 401 em `/auth/login` é senha errada e volta como erro normal para o formulário.
- Todo redirect para login passa por `redirectToLogin()`, e todo redirect pós-login passa por `safeRedirectPath()` (bloqueia open redirect). Não monte esses redirects à mão.
- Rotas autenticadas ficam no route group `src/app/(app)/` (`/dashboard`, `/transactions`, `/settings`). O `(app)/layout.tsx` faz a guarda (só UX; quem protege os dados é o backend) e renderiza a navegação (`AppNav`). Página nova da área logada = pasta dentro de `(app)/` + item em `NAV_ITEMS` (`src/components/app-nav.tsx`).
- Chamadas à API partem de **Client Components** (`"use client"`) via `api`. As páginas saem prerenderizadas (`cacheComponents`), então a guarda de sessão roda no cliente; não leia o cookie em Server Components sem pensar no impacto na prerenderização.
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
| POST | `/auth/login` | `SessionUser` (Route Handler do Next; o `TokenResponse` do backend fica no servidor) |
| GET | `/auth/session` | `SessionUser` ou 401 (Route Handler do Next, lê o cookie) |
| POST | `/auth/logout` | 204 (Route Handler do Next, apaga o cookie) |
| GET/POST | `/transactions` | `Page<Transaction>` / `Transaction` |
| GET/PUT/DELETE | `/transactions/{id}` | `Transaction` / 204 |
| POST | `/transactions/import` | `ImportSummaryResponse` (multipart, campo `file`, OFX ou CSV, até 5MB) |
| GET/POST | `/categories` | `Category[]` / `Category` |
| GET/PUT/DELETE | `/categories/{id}` | `Category` / 204 |
| GET/POST | `/category-rules` | `Page<CategoryRule>` / `CategoryRule` |
| GET/PUT/DELETE | `/category-rules/{id}` | `CategoryRule` / 204 |
| POST | `/category-rules/apply-retroactive` | `RetroactiveCategorizationResponse` |
| GET | `/reports/monthly?year=&month=` | `MonthlyReportResponse` |

Não existe endpoint `/me` no backend: quem está logado vem de `GET /api/auth/session`, que decodifica o cookie no Next.

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
- `signIn(page, path, user?)` entra pelo `POST /api/auth/login` real (via `page.request`, que divide os cookies com a página) e abre `path`. `loginViaApi(page)` só entra, sem navegar: use quando o teste conta requisições no mock, para nenhuma chamada de fundo da página interferir.
- Usuários do mock: `SEED_USER`; `REVOKED_USER` (entra, mas a API recusa o token com 401); `RATE_LIMITED_EMAIL` (login leva 429 com `Retry-After: 900`).
- `lastMockRequest(request)` lê `/__last-request` do mock: contagem de requisições recebidas e headers da última (`authorization`, `cookie`, `x-forwarded-for`). Serve para provar o que o proxy repassa ou não.
- `page.request` não manda `Origin` sozinho: em POST/PUT/DELETE para `/api`, passe `headers: { Origin: APP_ORIGIN }`, senão leva 403.
