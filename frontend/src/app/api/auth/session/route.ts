import { NextResponse, type NextRequest } from "next/server";

import { rejectCrossSite } from "@/lib/server/csrf";
import { NO_STORE, unauthenticated } from "@/lib/server/http";
import { clearSessionCookie, decodeSessionToken, readSessionToken } from "@/lib/server/session";

/**
 * Quem está logado, a partir do cookie. Decodifica sem conferir a assinatura: serve
 * só para a interface. Não chama o backend; quem valida o token é ele, a cada chamada.
 */
export function GET(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;

  const token = readSessionToken(request);
  const user = token ? decodeSessionToken(token) : null;
  if (user) return NextResponse.json(user, { headers: NO_STORE });

  const response = unauthenticated();
  // Cookie malformado ou vencido: apaga já, para não ser lido de novo
  if (token) clearSessionCookie(response);
  return response;
}
