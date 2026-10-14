import { NextResponse, type NextRequest } from "next/server";

import { rejectCrossSite } from "@/lib/server/csrf";
import { NO_STORE } from "@/lib/server/http";
import { clearSessionCookie } from "@/lib/server/session";

/**
 * Apaga o cookie de sessão. Idempotente. O backend é stateless e não tem o que
 * revogar: um token copiado antes continua valendo até o `exp`.
 */
export function POST(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;

  const response = new NextResponse(null, { status: 204, headers: NO_STORE });
  clearSessionCookie(response);
  return response;
}
