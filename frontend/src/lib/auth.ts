import { jwtDecode } from "jwt-decode";

import type { Role, UUID } from "@/types";

const TOKEN_KEY = "financas.token";
// O evento "storage" só dispara em outras abas; este avisa a aba atual
const TOKEN_CHANGE_EVENT = "financas:token-change";

export const DEFAULT_AFTER_LOGIN = "/dashboard";

// localStorage só existe no browser: em Server Components não há token e as
// chamadas autenticadas devem partir de Client Components.
export const tokenStorage = {
  get(): string | null {
    if (typeof window === "undefined") return null;
    return window.localStorage.getItem(TOKEN_KEY);
  },
  set(token: string): void {
    window.localStorage.setItem(TOKEN_KEY, token);
    window.dispatchEvent(new Event(TOKEN_CHANGE_EVENT));
  },
  clear(): void {
    if (typeof window === "undefined") return;
    window.localStorage.removeItem(TOKEN_KEY);
    window.dispatchEvent(new Event(TOKEN_CHANGE_EVENT));
  },
  /** Para useSyncExternalStore: avisa quando o token muda nesta aba ou em outra */
  subscribe(onChange: () => void): () => void {
    const onStorage = (event: StorageEvent) => {
      if (event.key === TOKEN_KEY || event.key === null) onChange();
    };
    window.addEventListener(TOKEN_CHANGE_EVENT, onChange);
    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener(TOKEN_CHANGE_EVENT, onChange);
      window.removeEventListener("storage", onStorage);
    };
  },
};

// Claims emitidos pelo JwtService do backend. Atenção: `sub` é o UUID do
// usuário, não o e-mail; o e-mail vem no claim `email`.
interface TokenClaims {
  sub: UUID;
  email: string;
  role: Role;
  exp: number;
}

export interface AuthUser {
  id: UUID;
  email: string;
  role: Role;
  expiresAt: Date;
}

/**
 * Lê os claims do token SEM validar a assinatura (isso é papel do backend).
 * Retorna null para token malformado, sem os claims esperados ou já expirado.
 */
export function decodeToken(token: string): AuthUser | null {
  let claims: Partial<TokenClaims>;
  try {
    claims = jwtDecode<Partial<TokenClaims>>(token);
  } catch {
    return null;
  }

  const { sub, email, role, exp } = claims;
  if (!sub || !email || !role || typeof exp !== "number") return null;
  if (exp * 1000 <= Date.now()) return null;

  return { id: sub, email, role, expiresAt: new Date(exp * 1000) };
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

// Evita redirects concorrentes (vários 401 em paralelo, logout + guarda de rota)
let redirectingToLogin = false;

/**
 * Limpa a sessão e vai para /login com navegação completa: funciona fora do
 * React (interceptor) e descarta qualquer estado em memória do usuário anterior.
 */
export function redirectToLogin({ keepFrom }: { keepFrom: boolean }): void {
  tokenStorage.clear();
  if (typeof window === "undefined" || redirectingToLogin) return;
  if (window.location.pathname === "/login") return;

  redirectingToLogin = true;
  const from = window.location.pathname + window.location.search;
  const target = keepFrom ? `/login?from=${encodeURIComponent(from)}` : "/login";
  window.location.assign(target);
}
