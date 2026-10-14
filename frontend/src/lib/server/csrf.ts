import "server-only";

import type { NextRequest } from "next/server";

import { problem } from "@/lib/server/http";

const SAFE_METHODS = new Set(["GET", "HEAD"]);

let warnedMissingAppOrigin = false;

/**
 * Origem do app (esquema + host + porta) para comparar com o header Origin.
 * Em produção vem só de APP_ORIGIN: o Host que chega ao Next depende de como o
 * proxy reverso foi configurado (um proxy_pass padrão manda o host interno).
 * Sem a env em produção, retorna null e toda mutação é recusada.
 */
function appOrigin(request: NextRequest): string | null {
  const configured = process.env.APP_ORIGIN;
  if (configured) {
    try {
      return new URL(configured).origin;
    } catch {
      return reportMissingAppOrigin(`APP_ORIGIN inválida: '${configured}'`);
    }
  }
  if (process.env.NODE_ENV !== "production") return request.nextUrl.origin;
  return reportMissingAppOrigin("APP_ORIGIN não definida");
}

function reportMissingAppOrigin(reason: string): null {
  if (!warnedMissingAppOrigin) {
    warnedMissingAppOrigin = true;
    console.error(`${reason}: todo POST/PUT/PATCH/DELETE em /api será recusado com 403`);
  }
  return null;
}

/**
 * Proteção contra CSRF em todos os Route Handlers de /api. Retorna a resposta 403
 * a devolver, ou null se a requisição pode seguir.
 *
 * - `Sec-Fetch-Site: cross-site` é recusado em qualquer método. Sem o header
 *   (curl, navegador antigo) a checagem passa, mas mutações ainda exigem Origin.
 * - Fora de GET/HEAD, o Origin tem que existir e ser igual à origem do app.
 *   Isso também cobre CSRF de login (logar a vítima na conta do atacante).
 */
export function rejectCrossSite(request: NextRequest): Response | null {
  if (request.headers.get("sec-fetch-site") === "cross-site") {
    return problem(403, "Acesso negado", "Requisição de outro site recusada");
  }
  if (SAFE_METHODS.has(request.method)) return null;

  const origin = request.headers.get("origin");
  const expected = appOrigin(request);
  if (!origin || !expected || origin !== expected) {
    return problem(403, "Acesso negado", "Origem da requisição não permitida");
  }
  return null;
}
