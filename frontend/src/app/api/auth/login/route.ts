import { NextResponse, type NextRequest } from "next/server";

import { backendHeaders, backendUnavailable, fetchBackend, readBodyWithLimit, toClientResponse } from "@/lib/server/backend";
import { rejectCrossSite } from "@/lib/server/csrf";
import { NO_STORE, problem } from "@/lib/server/http";
import { decodeSessionToken, setSessionCookie } from "@/lib/server/session";
import type { TokenResponse } from "@/types";

// E-mail (até 255) e senha (até 72 bytes) cabem com muita folga
const MAX_BODY_BYTES = 8 * 1024;

/**
 * Login pelo servidor: o token do backend vai para o cookie httpOnly e o navegador
 * recebe só os dados do usuário. Erros do backend (400, 401, 429 com Retry-After)
 * chegam ao navegador como vieram.
 */
export async function POST(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;

  const body = await readBodyWithLimit(request, MAX_BODY_BYTES);
  if (!body) return problem(413, "Requisição muito grande");

  const upstream = await fetchBackend(request, "/auth/login", {
    method: "POST",
    headers: backendHeaders(request, null),
    body,
  });
  if (!upstream) return backendUnavailable();
  if (!upstream.ok) return toClientResponse(upstream);

  const data = (await upstream.json().catch(() => null)) as Partial<TokenResponse> | null;
  const token = data?.accessToken;
  const maxAge = data?.expiresIn;
  const user = typeof token === "string" ? decodeSessionToken(token) : null;
  if (!user || !token || typeof maxAge !== "number" || !Number.isInteger(maxAge) || maxAge <= 0) {
    return problem(502, "Resposta inválida do backend", "O servidor retornou um token inválido.");
  }

  const response = NextResponse.json(user, { headers: NO_STORE });
  setSessionCookie(response, token, maxAge);
  return response;
}
