import "server-only";

import { NextResponse } from "next/server";

/** Respostas de /api carregam dados do usuário ou o cookie de sessão: nunca entram em cache */
export const NO_STORE = { "Cache-Control": "no-store" } as const;

/** Erro no formato ProblemDetail (RFC 7807), o mesmo do backend: o getErrorMessage lê o `detail` */
export function problem(status: number, title: string, detail?: string): NextResponse {
  return NextResponse.json(
    { type: "about:blank", title, status, ...(detail && { detail }) },
    { status, headers: { ...NO_STORE, "Content-Type": "application/problem+json" } },
  );
}

/** Mesma resposta do backend para quem chega sem sessão em rota protegida */
export function unauthenticated(): NextResponse {
  return problem(401, "Não autenticado", "Token ausente, inválido ou expirado");
}
