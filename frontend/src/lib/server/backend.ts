import "server-only";

import { NextResponse, type NextRequest } from "next/server";

import { NO_STORE, problem } from "@/lib/server/http";

// Lido só no servidor (sem NEXT_PUBLIC_): o navegador nunca fala direto com o backend
const BACKEND_URL = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");

const BACKEND_TIMEOUT_MS = 30_000;

// Únicos headers que passam em cada sentido. Ficam de fora, entre outros, o Cookie
// e o Authorization do navegador: o Authorization é montado aqui, a partir do cookie.
// O X-Forwarded-For vai como chegou (o Next só o preenche quando o cliente não manda):
// é ele que leva o IP do cliente ao rate limit do /auth (veja TRUSTED_PROXIES no README).
const REQUEST_HEADERS = ["accept", "accept-language", "content-type", "x-forwarded-for"];
// Sem Content-Encoding/Content-Length: o fetch já descompactou o corpo. Sem Location
// (aponta para o host interno do backend) e sem Set-Cookie.
const RESPONSE_HEADERS = ["content-type", "retry-after"];

export function backendHeaders(request: NextRequest, token: string | null): Headers {
  const headers = new Headers();
  for (const name of REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  if (token) headers.set("authorization", `Bearer ${token}`);
  return headers;
}

/**
 * Lê o corpo inteiro com teto de bytes. Route Handlers não têm limite próprio: sem
 * isso, um corpo gigante iria todo para a memória do Next. Retorna null se passar do teto.
 */
export async function readBodyWithLimit(
  request: NextRequest,
  maxBytes: number,
): Promise<Uint8Array<ArrayBuffer> | null> {
  if (Number(request.headers.get("content-length")) > maxBytes) return null;
  if (!request.body) return new Uint8Array(0);

  const chunks: Uint8Array[] = [];
  let total = 0;
  const reader = request.body.getReader();
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    // Content-Length ausente (chunked) ou mentiroso: conta o que realmente chegou
    if (total > maxBytes) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }

  const body = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return body;
}

/**
 * Chama o backend. Não segue redirect (levaria o Authorization para onde o backend
 * mandar) e tem timeout; cancelar a requisição no navegador cancela esta também.
 * Retorna null se o backend não respondeu ou respondeu com redirect.
 */
export async function fetchBackend(
  request: NextRequest,
  path: string,
  init: { method: string; headers: Headers; body?: BodyInit },
): Promise<Response | null> {
  try {
    const upstream = await fetch(`${BACKEND_URL}${path}`, {
      ...init,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.any([request.signal, AbortSignal.timeout(BACKEND_TIMEOUT_MS)]),
    });
    if (upstream.status >= 300 && upstream.status < 400) {
      await upstream.body?.cancel();
      return null;
    }
    return upstream;
  } catch {
    return null;
  }
}

/** 502 sem `detail`: o getErrorMessage mostra "Não foi possível conectar ao servidor" */
export function backendUnavailable(): NextResponse {
  return problem(502, "Backend indisponível");
}

/** Mesma resposta do backend (status e corpo em stream), só com os headers da lista e sem cache */
export function toClientResponse(upstream: Response): NextResponse {
  const headers = new Headers(NO_STORE);
  for (const name of RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  const hasBody = upstream.status !== 204 && upstream.status !== 304;
  return new NextResponse(hasBody ? upstream.body : null, { status: upstream.status, headers });
}
