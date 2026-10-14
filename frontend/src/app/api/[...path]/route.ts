import { NextResponse, type NextRequest } from "next/server";

import { backendHeaders, backendUnavailable, fetchBackend, readBodyWithLimit, toClientResponse } from "@/lib/server/backend";
import { rejectCrossSite } from "@/lib/server/csrf";
import { NO_STORE, problem, unauthenticated } from "@/lib/server/http";
import { clearSessionCookie, readSessionToken } from "@/lib/server/session";

// Proxy de /api/* para o backend: lê o cookie de sessão e injeta o Authorization.
// /api/auth/login, /logout e /session têm Route Handlers próprios, que vencem este.

const API_PREFIX = "/api/";

// Só palavras, números e UUIDs separados por uma barra: é tudo que as rotas do backend
// usam. Fica de fora qualquer escape (%2F, %2e, %5C), "\", ";" (matrix params do
// Spring), barra dupla e os segmentos "." e ".."
const SAFE_PATH = /^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/;
const SAFE_SEGMENT = /^[A-Za-z0-9_-]+$/;

// Recursos do backend que o app usa (primeiro segmento). Qualquer outro caminho dá 404
// sem chamar o backend: nada de Swagger (/v3/api-docs), actuator ou rotas futuras
// que ninguém decidiu expor. Recurso novo no app = entrada nova aqui.
const BACKEND_RESOURCES = new Set(["transactions", "categories", "category-rules", "reports"]);

// Único /auth/* que passa por aqui, e sem token. Qualquer outra variante de /auth
// (ex.: /auth/login com sufixo) devolveria o token cru ao JavaScript.
const PUBLIC_PATH = "auth/register";

// O backend recusa acima de 5 MB (max-request-size); a folga cobre o boundary e os
// cabeçalhos das partes, para quem decidir o limite continuar sendo o backend
const MULTIPART_MAX_BYTES = 5 * 1024 * 1024 + 64 * 1024;
const BODY_MAX_BYTES = 1024 * 1024;

const ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";

/** Caminho no backend, ou null se o path tiver qualquer coisa fora do formato esperado */
function backendPath(request: NextRequest, segments: string[]): string | null {
  // Confere o path cru e os segmentos que o Next decodificou (um %2F vira "/" dentro
  // de um segmento), e exige que os dois batam: valida exatamente o que foi roteado
  const raw = request.nextUrl.pathname.slice(API_PREFIX.length);
  if (!SAFE_PATH.test(raw) || !segments.every((segment) => SAFE_SEGMENT.test(segment))) return null;
  if (raw !== segments.join("/")) return null;
  return raw;
}

async function handle(request: NextRequest, { params }: RouteContext<"/api/[...path]">): Promise<Response> {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;

  const path = backendPath(request, (await params).path);
  if (!path) return problem(400, "Requisição inválida", "Caminho inválido");

  const isPublic = path === PUBLIC_PATH;
  if (!isPublic && !BACKEND_RESOURCES.has(path.split("/")[0])) {
    return problem(404, "Não encontrado", "Rota não encontrada");
  }

  // Sem sessão em rota protegida: responde aqui, sem ler o corpo nem chamar o backend
  const token = isPublic ? null : readSessionToken(request);
  if (!isPublic && !token) return unauthenticated();

  let body: Uint8Array<ArrayBuffer> | undefined;
  if (request.method !== "GET" && request.method !== "HEAD") {
    const isMultipart = request.headers.get("content-type")?.toLowerCase().startsWith("multipart/form-data");
    const read = await readBodyWithLimit(request, isMultipart ? MULTIPART_MAX_BYTES : BODY_MAX_BYTES);
    if (!read) {
      return isMultipart
        ? problem(413, "Arquivo muito grande", "O arquivo passa do limite de 5 MB.")
        : problem(413, "Requisição muito grande");
    }
    if (read.byteLength > 0) body = read;
  }

  const upstream = await fetchBackend(request, `/${path}${request.nextUrl.search}`, {
    method: request.method,
    headers: backendHeaders(request, token),
    body,
  });
  if (!upstream) return backendUnavailable();

  const response = toClientResponse(upstream);
  // Token recusado (expirado ou revogado): apaga o cookie já aqui. Se o logout do
  // cliente falhar, o /login não vê uma sessão que não vale mais e não manda de volta.
  if (upstream.status === 401 && token) clearSessionCookie(response);
  return response;
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;

// Explícito para o guard também valer aqui. Sem headers de CORS: preflight de outra
// origem nunca é aprovado.
export function OPTIONS(request: NextRequest) {
  const rejected = rejectCrossSite(request);
  if (rejected) return rejected;
  return new NextResponse(null, { status: 204, headers: { ...NO_STORE, Allow: ALLOWED_METHODS } });
}
