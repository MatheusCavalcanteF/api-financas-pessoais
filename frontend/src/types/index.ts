// Espelho dos DTOs do backend (api-financas-pessoais). Ao mudar um record lá, atualize aqui.
//
// Serialização do Jackson:
// - UUID     -> string
// - Instant  -> ISO-8601 em UTC, ex. "2026-10-07T14:30:00Z"
// - LocalDate -> "yyyy-MM-dd" (sem fuso; não passe por `new Date()` ou o dia pode mudar)
// - BigDecimal -> number JSON. Exibição ok; para somar valores, prefira os totais do backend.

export type UUID = string;
export type IsoInstant = string;
export type IsoDate = string;

export type Role = "USER" | "ADMIN";
export type TransactionType = "INCOME" | "EXPENSE";

// ---------- Respostas ----------

/** UserResponse — retornado por POST /auth/register */
export interface User {
  id: UUID;
  email: string;
  role: Role;
  createdAt: IsoInstant;
}

/** TokenResponse — retornado por POST /auth/login */
export interface TokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  /** Validade do token em segundos */
  expiresIn: number;
}

/** TransactionResponse */
export interface Transaction {
  id: UUID;
  /** Sempre positivo; o sentido vem de `type` */
  amount: number;
  date: IsoDate;
  description: string;
  type: TransactionType;
  categoryId: UUID | null;
  createdAt: IsoInstant;
}

/** CategoryResponse */
export interface Category {
  id: UUID;
  name: string;
  createdAt: IsoInstant;
}

/** CategoryRuleResponse */
export interface CategoryRule {
  id: UUID;
  /** Trecho procurado na descrição, sem diferenciar maiúsculas nem acentos */
  keyword: string;
  categoryId: UUID;
  createdAt: IsoInstant;
}

/** CategoryExpenseResponse */
export interface CategoryExpense {
  /** `null` = grupo "Sem categoria". Use o id, não o nome, para identificá-lo. */
  categoryId: UUID | null;
  categoryName: string;
  total: number;
}

/** MonthlyReportResponse — GET /reports/monthly?year=&month= */
export interface MonthlyReportResponse {
  year: number;
  /** 1–12 */
  month: number;
  totalIncome: number;
  totalExpense: number;
  /** totalIncome - totalExpense; negativo se gastou mais do que recebeu */
  balance: number;
  /** Do maior para o menor total; a soma é `totalExpense` */
  expensesByCategory: CategoryExpense[];
}

/** ImportSummaryResponse — POST /transactions/import */
export interface ImportSummaryResponse {
  /** Linhas de dados lidas (sem cabeçalho e linhas vazias) */
  totalLines: number;
  imported: number;
  skippedDuplicates: number;
}

/** RetroactiveCategorizationResponse — POST /category-rules/apply-retroactive */
export interface RetroactiveCategorizationResponse {
  updatedCount: number;
}

/** Page<T> do Spring Data com `serialization-mode: via-dto` (PagedModel) */
export interface Page<T> {
  content: T[];
  page: {
    size: number;
    /** Índice da página, começando em 0 */
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

/** Corpo de erro do backend: ProblemDetail (RFC 7807) */
export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  /**
   * Validação de body/parâmetros: campo -> mensagem.
   * Importação de extrato inválido: lista de erros, um por linha do arquivo.
   */
  errors?: Record<string, string> | string[];
}

// ---------- Requisições ----------

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  /** Mínimo 8 caracteres, máximo 72 bytes (limite do BCrypt) */
  password: string;
}

export interface TransactionRequest {
  /** Positivo, até 2 casas decimais */
  amount: number;
  date: IsoDate;
  description: string;
  type: TransactionType;
  categoryId?: UUID | null;
}

export interface CategoryRequest {
  name: string;
}

export interface CategoryRuleRequest {
  /** Ao menos 3 caracteres além de espaços, máximo 100 */
  keyword: string;
  categoryId: UUID;
}
