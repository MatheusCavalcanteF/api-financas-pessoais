import type { Role, UUID } from "@/types";

export const DEFAULT_AFTER_LOGIN = "/dashboard";

// O JWT vive num cookie httpOnly que o JavaScript não lê nem apaga: quem grava e
// apaga são os Route Handlers de /api/auth. Aqui só ficam os dados para exibir.
export interface AuthUser {
  id: UUID;
  email: string;
  role: Role;
  expiresAt: Date;
}

// Chave em que versões antigas guardavam o JWT no localStorage
const LEGACY_TOKEN_KEY = "financas.token";

/** Apaga o token que versões antigas deixaram no localStorage */
export function clearLegacyToken(): void {
  try {
    window.localStorage.removeItem(LEGACY_TOKEN_KEY);
  } catch {
    // localStorage bloqueado (modo privado, política do navegador): não há o que apagar
  }
}

// Cookie não dispara evento entre abas como o "storage" do localStorage: login e
// logout avisam as outras abas por aqui, e elas consultam a sessão de novo.
// Um canal só por aba: o BroadcastChannel não entrega a mensagem ao próprio emissor.
let authChannel: BroadcastChannel | null | undefined;

function getAuthChannel(): BroadcastChannel | null {
  if (authChannel === undefined) {
    authChannel = typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel("financas:auth");
  }
  return authChannel;
}

export function notifyAuthChange(): void {
  getAuthChannel()?.postMessage("changed");
}

/** Avisa quando outra aba entrou ou saiu */
export function subscribeAuthChange(onChange: () => void): () => void {
  const channel = getAuthChannel();
  if (!channel) return () => {};
  channel.addEventListener("message", onChange);
  return () => channel.removeEventListener("message", onChange);
}

/**
 * Valida o `?from=` para não virar open redirect: só aceita caminhos do próprio
 * site. Recusa "//evil.com", "/\evil.com" e variantes com tab/quebra de linha,
 * que o parser de URL do browser normaliza para outro host.
 */
export function safeRedirectPath(from: string | null | undefined): string {
  if (!from || !from.startsWith("/")) return DEFAULT_AFTER_LOGIN;

  const base = "http://same-origin.invalid";
  let url: URL;
  try {
    url = new URL(from, base);
  } catch {
    return DEFAULT_AFTER_LOGIN;
  }
  if (url.origin !== base || url.pathname === "/login") return DEFAULT_AFTER_LOGIN;

  return url.pathname + url.search + url.hash;
}

async function logout(): Promise<void> {
  try {
    // fetch direto, não a instância `api`: lib/api importa este módulo (ciclo) e o
    // interceptor de 401 chamaria redirectToLogin de novo
    await fetch("/api/auth/logout", { method: "POST", signal: AbortSignal.timeout(5000) });
  } catch {
    // Sem resposta: segue para o login. Num 401 da API o catch-all já apagou o cookie.
  }
  notifyAuthChange();
}

// Evita redirects concorrentes (vários 401 em paralelo, logout + guarda de rota)
let redirectingToLogin = false;

/**
 * Encerra a sessão e vai para /login com navegação completa: funciona fora do
 * React (interceptor) e descarta qualquer estado em memória do usuário anterior.
 *
 * `clearSession: false` pula o logout quando já se sabe que não há cookie válido
 * (guarda de rota depois de /api/auth/session responder 401).
 */
export async function redirectToLogin({
  keepFrom,
  clearSession = true,
}: {
  keepFrom: boolean;
  clearSession?: boolean;
}): Promise<void> {
  if (typeof window === "undefined" || redirectingToLogin) return;
  const onLoginPage = window.location.pathname === "/login";
  if (!onLoginPage) redirectingToLogin = true;

  if (clearSession) await logout();
  if (onLoginPage) return;

  const from = window.location.pathname + window.location.search;
  const target = keepFrom ? `/login?from=${encodeURIComponent(from)}` : "/login";
  window.location.assign(target);
}
