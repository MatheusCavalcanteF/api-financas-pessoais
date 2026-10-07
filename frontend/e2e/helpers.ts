import { test as base, type APIRequestContext, type Page } from "@playwright/test";

export { expect } from "@playwright/test";
export type { APIRequestContext, Page } from "@playwright/test";

export const MOCK_URL = "http://localhost:18080";

// Manter em sincronia com e2e/mock-backend.mjs
export const SEED_USER = { email: "teste@exemplo.com", password: "senha-segura-123" };
export const REVOKED_EMAIL = "revogado@exemplo.com";

const TOKEN_KEY = "financas.token";

export const test = base.extend<{ resetBackend: void; blockExternalRequests: void }>({
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
      await page.route(/^https?:\/\/(?!localhost[:/])/, (route) => route.abort());
      await use();
    },
    { auto: true },
  ],
});

/** JWT sem assinatura válida, com os mesmos claims do JwtService (o mock não confere assinatura) */
export function fakeToken(email = SEED_USER.email): string {
  const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const claims = { sub: "11111111-1111-1111-1111-111111111111", email, role: "USER", exp: Math.floor(Date.now() / 1000) + 3600 };
  return `${b64({ alg: "HS256" })}.${b64(claims)}.mock`;
}

/** Entra direto com um token no localStorage (pula o formulário) e abre `path` */
export async function signIn(page: Page, path: string, email = SEED_USER.email): Promise<void> {
  await page.goto("/login");
  await page.evaluate(([key, token]) => localStorage.setItem(key, token), [TOKEN_KEY, fakeToken(email)]);
  await page.goto(path);
}

export async function readToken(page: Page): Promise<string | null> {
  return page.evaluate((key) => localStorage.getItem(key), TOKEN_KEY);
}

/** Registro de chamadas relevantes feitas ao mock (DELETEs, imports, páginas de regras) */
export async function mockLog(request: APIRequestContext): Promise<string[]> {
  return (await request.get(`${MOCK_URL}/__log`)).json();
}

const brlFormatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
/** Mesmo formato que o app exibe (inclui o espaço não separável depois de "R$") */
export const brl = (value: number) => brlFormatter.format(value);
