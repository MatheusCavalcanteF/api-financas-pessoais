import axios, { isAxiosError, type AxiosError } from "axios";

import { redirectToLogin } from "@/lib/auth";
import type {
  ApiError,
  Category,
  CategoryRequest,
  CategoryRule,
  CategoryRuleRequest,
  ImportSummaryResponse,
  LoginRequest,
  MonthlyReportResponse,
  Page,
  RegisterRequest,
  RetroactiveCategorizationResponse,
  SessionUser,
  Transaction,
  User,
  UUID,
} from "@/types";

// /api/* são Route Handlers do Next (src/app/api): o browser só fala com a própria
// origem, sem CORS. O JWT fica num cookie httpOnly que o navegador manda sozinho, e
// o Route Handler é quem injeta o Authorization na chamada ao backend.
export const API_BASE_URL = "/api";

// Rotas de autenticação: um 401 nelas é credencial inválida (deve aparecer no
// formulário) ou ausência de sessão, não sessão expirada. Não redirecionam.
const AUTH_PATHS = ["/auth/login", "/auth/register", "/auth/session", "/auth/logout"];

function isAuthPath(url: string | undefined): boolean {
  if (!url) return false;
  const path = url.split(/[?#]/)[0];
  return AUTH_PATHS.includes(path.startsWith("/") ? path : `/${path}`);
}

// Sem Content-Type fixo: o axios usa JSON para objetos e multipart para FormData.
// Forçar "application/json" faria o FormData do /transactions/import virar JSON.
export const api = axios.create({ baseURL: API_BASE_URL });

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401 && !isAuthPath(error.config?.url)) {
      void redirectToLogin({ keepFrom: true });
    }
    return Promise.reject(error);
  },
);

/** Mensagem legível para exibir ao usuário a partir de um erro da API */
export function getErrorMessage(error: unknown): string {
  if (!isAxiosError<ApiError>(error)) return "Erro inesperado. Tente novamente.";

  const data = error.response?.data;
  // Sem resposta, ou o proxy do Next não alcançou o backend
  if (!error.response || (error.response.status >= 500 && !data?.detail)) {
    return "Não foi possível conectar ao servidor. Tente novamente em instantes.";
  }

  // Erro de validação: mostra a primeira mensagem de campo, que é mais específica.
  // Listas (importação) ficam no `detail`; os itens saem em getErrorList.
  const errors = data?.errors;
  const fieldError = errors && !Array.isArray(errors) && Object.entries(errors)[0];
  if (fieldError) return `${fieldError[0]}: ${fieldError[1]}`;

  if (data?.detail) return data.detail;
  return error.response.status < 500 ? "Verifique os dados informados." : "Erro inesperado. Tente novamente.";
}

/** Status HTTP da resposta de erro, se houve resposta */
export function getErrorStatus(error: unknown): number | undefined {
  return isAxiosError(error) ? error.response?.status : undefined;
}

/** Itens de erro em formato de lista (ex.: linhas inválidas de um extrato importado) */
export function getErrorList(error: unknown): string[] {
  if (!isAxiosError<ApiError>(error)) return [];
  const errors = error.response?.data?.errors;
  return Array.isArray(errors) ? errors : [];
}

// ---------- Endpoints ----------

interface RequestOptions {
  /** Cancela a requisição, ex. quando o componente troca de filtro antes da resposta */
  signal?: AbortSignal;
}

/** POST /api/auth/login — Route Handler: grava o cookie de sessão e devolve só o usuário, nunca o token */
export async function login(credentials: LoginRequest): Promise<SessionUser> {
  const { data } = await api.post<SessionUser>("/auth/login", credentials);
  return data;
}

/** POST /auth/register — público; não abre sessão (o cadastro faz login em seguida) */
export async function register(request: RegisterRequest): Promise<User> {
  const { data } = await api.post<User>("/auth/register", request);
  return data;
}

/** GET /api/auth/session — usuário do cookie de sessão; 401 se não houver sessão válida */
export async function getSession({ signal }: RequestOptions = {}): Promise<SessionUser> {
  const { data } = await api.get<SessionUser>("/auth/session", { signal });
  return data;
}

/** GET /reports/monthly — month vai de 1 a 12 */
export async function getMonthlyReport(
  year: number,
  month: number,
  { signal }: RequestOptions = {},
): Promise<MonthlyReportResponse> {
  const { data } = await api.get<MonthlyReportResponse>("/reports/monthly", {
    params: { year, month },
    signal,
  });
  return data;
}

/** GET /transactions — ordenado por data, mais recentes primeiro (padrão do backend) */
export async function listTransactions(
  { page, size }: { page: number; size: number },
  { signal }: RequestOptions = {},
): Promise<Page<Transaction>> {
  const { data } = await api.get<Page<Transaction>>("/transactions", { params: { page, size }, signal });
  return data;
}

/** GET /categories — todas as categorias do usuário (sem paginação) */
export async function listCategories({ signal }: RequestOptions = {}): Promise<Category[]> {
  const { data } = await api.get<Category[]>("/categories", { signal });
  return data;
}

/** POST /categories — 409 se já existir uma com o mesmo nome (sem diferenciar maiúsculas) */
export async function createCategory(request: CategoryRequest): Promise<Category> {
  const { data } = await api.post<Category>("/categories", request);
  return data;
}

/**
 * DELETE /categories/{id} — 409 se houver transações vinculadas. Sem transações,
 * as regras da categoria são apagadas junto (ON DELETE CASCADE no banco).
 */
export async function deleteCategory(id: UUID): Promise<void> {
  await api.delete(`/categories/${id}`);
}

// Máximo aceito pelo backend (spring.data.web.pageable.max-page-size)
const RULES_PAGE_SIZE = 100;

/**
 * GET /category-rules, todas as páginas, em ordem alfabética de palavra-chave.
 * O backend limita a 500 regras por usuário, então são no máximo 5 requisições.
 */
export async function listAllCategoryRules({ signal }: RequestOptions = {}): Promise<CategoryRule[]> {
  const rules: CategoryRule[] = [];
  for (let page = 0; ; page++) {
    const { data } = await api.get<Page<CategoryRule>>("/category-rules", {
      params: { page, size: RULES_PAGE_SIZE, sort: "keyword,asc" },
      signal,
    });
    rules.push(...data.content);
    if (page + 1 >= data.page.totalPages) return rules;
  }
}

/** POST /category-rules — 409 se a palavra-chave já existir ou no limite de regras; 404 se a categoria sumiu */
export async function createCategoryRule(request: CategoryRuleRequest): Promise<CategoryRule> {
  const { data } = await api.post<CategoryRule>("/category-rules", request);
  return data;
}

/** DELETE /category-rules/{id} */
export async function deleteCategoryRule(id: UUID): Promise<void> {
  await api.delete(`/category-rules/${id}`);
}

/** Limite do backend (spring.servlet.multipart.max-file-size) */
export const IMPORT_MAX_BYTES = 5 * 1024 * 1024;

/**
 * POST /transactions/import — extrato OFX ou CSV. Idempotente: reenviar o mesmo
 * arquivo só gera duplicadas ignoradas. Qualquer linha inválida rejeita o arquivo
 * inteiro (400, com as linhas em getErrorList).
 */
export async function importStatement(file: File): Promise<ImportSummaryResponse> {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<ImportSummaryResponse>("/transactions/import", form);
  return data;
}

/** POST /category-rules/apply-retroactive — categoriza transações ainda sem categoria */
export async function applyRulesRetroactively(): Promise<RetroactiveCategorizationResponse> {
  const { data } = await api.post<RetroactiveCategorizationResponse>("/category-rules/apply-retroactive");
  return data;
}
