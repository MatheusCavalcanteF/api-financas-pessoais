import {
  test as base,
  expect,
  type APIRequestContext,
  type BrowserContext,
  type Cookie,
  type Page,
} from "@playwright/test";

export { expect } from "@playwright/test";
export type { APIRequestContext, Page } from "@playwright/test";

export const MOCK_URL = "http://localhost:18080";
// Manter em sincronia com APP_PORT/APP_ORIGIN em playwright.config.ts
export const APP_ORIGIN = "http://localhost:3100";

// Manter em sincronia com e2e/mock-backend.mjs
export const SEED_USER = { email: "teste@exemplo.com", password: "senha-segura-123" };
/** Entra normalmente, mas a API recusa o token dele com 401 (sessão expirada ou revogada) */
export const REVOKED_USER = { email: "revogado@exemplo.com", password: "senha-revogada-123" };
/** Login com este e-mail leva 429 com Retry-After: 900 */
export const RATE_LIMITED_EMAIL = "bloqueado@exemplo.com";

// A suíte roda o build de produção, então o cookie leva o prefixo __Host-
// (manter em sincronia com src/lib/server/session.ts)
export const SESSION_COOKIE = "__Host-financas_session";
/** Onde versões antigas guardavam o JWT; o app apaga ao carregar */
export const LEGACY_TOKEN_KEY = "financas.token";

/** Tudo que não for localhost */
export const EXTERNAL_URL = /^https?:\/\/(?!localhost[:/])/;

/** O Chrome loga cada bloqueio no console: "Refused to ... violates the following Content Security Policy directive" */
const CSP_VIOLATION = /Content Security Policy/i;

/** Junta as violações de CSP que qualquer página do contexto registrar no console */
export function collectCspViolations(context: BrowserContext): string[] {
  const violations: string[] = [];
  context.on("console", (message) => {
    if (CSP_VIOLATION.test(message.text())) violations.push(`${message.page()?.url()}: ${message.text()}`);
  });
  return violations;
}

export const test = base.extend<{ resetBackend: void; blockExternalRequests: void; noCspViolations: void }>({
  /** Todo teste começa com o backend falso no estado inicial */
  resetBackend: [
    async ({ request }, use) => {
      await request.post(`${MOCK_URL}/__reset`);
      await use();
    },
    { auto: true },
  ],
  /**
   * Bloqueia tudo que não for localhost. Além de manter a suíte sem rede, evita
   * scripts injetados por antivírus (ex.: Kaspersky) que seguravam o `load`
   * da página por ~9s esperando DNS.
   */
  blockExternalRequests: [
    async ({ page }, use) => {
      await page.route(EXTERNAL_URL, (route) => route.abort());
      await use();
    },
    { auto: true },
  ],
  /** Todo fluxo também valida o CSP: nenhuma página pode registrar violação no console */
  noCspViolations: [
    async ({ context }, use) => {
      const violations = collectCspViolations(context);
      await use();
      expect(violations, "violações de CSP no console").toEqual([]);
    },
    { auto: true },
  ],
});

type Credentials = { email: string; password: string };

/**
 * Entra pelo Route Handler de login, sem abrir página nenhuma (pula o formulário).
 * O `page.request` divide os cookies com a página, então o cookie de sessão fica
 * no contexto. Sem navegação, nenhuma chamada de fundo da página chega ao mock.
 */
export async function loginViaApi(page: Page, user: Credentials = SEED_USER): Promise<void> {
  const response = await page.request.post("/api/auth/login", { data: user, headers: { Origin: APP_ORIGIN } });
  expect(response.status(), "login do helper").toBe(200);
}

/** Entra pelo Route Handler de login e abre `path` */
export async function signIn(page: Page, path: string, user: Credentials = SEED_USER): Promise<void> {
  await loginViaApi(page, user);
  await page.goto(path);
}

export async function sessionCookie(context: BrowserContext): Promise<Cookie | undefined> {
  return (await context.cookies()).find((cookie) => cookie.name === SESSION_COOKIE);
}

/** Tudo que o JavaScript da página consegue ler: o token não pode aparecer em nada disso */
export async function readClientStorage(
  page: Page,
): Promise<{ localStorage: Record<string, string>; sessionStorage: Record<string, string>; documentCookie: string }> {
  return page.evaluate(() => ({
    localStorage: { ...window.localStorage },
    sessionStorage: { ...window.sessionStorage },
    documentCookie: document.cookie,
  }));
}

export interface MockRequestLog {
  /** Requisições recebidas pelo mock desde que subiu (não zera no /__reset) */
  count: number;
  last: {
    method: string;
    path: string;
    authorization: string | null;
    cookie: string | null;
    xForwardedFor: string | null;
  } | null;
}

/** Última requisição que chegou ao mock e quantas chegaram até agora */
export async function lastMockRequest(request: APIRequestContext): Promise<MockRequestLog> {
  return (await request.get(`${MOCK_URL}/__last-request`)).json();
}

/** Registro de chamadas relevantes feitas ao mock (DELETEs, imports, páginas de regras) */
export async function mockLog(request: APIRequestContext): Promise<string[]> {
  return (await request.get(`${MOCK_URL}/__log`)).json();
}

const brlFormatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
/** Mesmo formato que o app exibe (inclui o espaço não separável depois de "R$") */
export const brl = (value: number) => brlFormatter.format(value);
