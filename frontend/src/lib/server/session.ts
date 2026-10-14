import "server-only";

import { jwtDecode } from "jwt-decode";
import type { NextRequest, NextResponse } from "next/server";

import type { Role, SessionUser, UUID } from "@/types";

const secure = process.env.NODE_ENV === "production";

// O prefixo __Host- exige Secure e Path=/ sem Domain: um subdomínio irmão não consegue
// gravar um cookie com esse nome por cima da sessão. No dev (sem Secure) o navegador
// recusaria o prefixo, então o nome muda.
export const SESSION_COOKIE = secure ? "__Host-financas_session" : "financas_session";

// Claims emitidos pelo JwtService do backend. Atenção: `sub` é o UUID do
// usuário, não o e-mail; o e-mail vem no claim `email`.
interface TokenClaims {
  sub: UUID;
  email: string;
  role: Role;
  exp: number;
}

/**
 * Lê os claims do token SEM validar a assinatura: serve só para exibir quem está
 * logado. Quem valida é o backend, a cada chamada. Retorna null para token
 * malformado, sem os claims esperados ou já expirado.
 */
export function decodeSessionToken(token: string): SessionUser | null {
  let claims: Partial<TokenClaims>;
  try {
    claims = jwtDecode<Partial<TokenClaims>>(token);
  } catch {
    return null;
  }

  const { sub, email, role, exp } = claims;
  if (!sub || !email || !role || typeof exp !== "number") return null;
  if (exp * 1000 <= Date.now()) return null;

  return { id: sub, email, role, expiresAt: new Date(exp * 1000).toISOString() };
}

export function readSessionToken(request: NextRequest): string | null {
  return request.cookies.get(SESSION_COOKIE)?.value || null;
}

export function setSessionCookie(response: NextResponse, token: string, maxAge: number): void {
  response.cookies.set(SESSION_COOKIE, token, { httpOnly: true, sameSite: "strict", secure, path: "/", maxAge });
}

export function clearSessionCookie(response: NextResponse): void {
  // Mesmos atributos do set: o navegador não apaga um cookie __Host- sem Secure e Path=/
  response.cookies.set(SESSION_COOKIE, "", { httpOnly: true, sameSite: "strict", secure, path: "/", maxAge: 0 });
}
