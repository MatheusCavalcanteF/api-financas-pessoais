import { AlertCircle, ChevronLeft, ChevronRight, Receipt, RefreshCw } from "lucide-react";

import { formatCurrency, formatDate, pluralize } from "@/lib/format";
import type { Category, Page, Transaction, UUID } from "@/types";

interface TransactionsTableProps {
  data: Page<Transaction> | null;
  loading: boolean;
  error: string | null;
  categories: Map<UUID, Category>;
  /** Enquanto true, categorias ainda não resolvidas aparecem como "…" (depois, "—") */
  categoriesLoading: boolean;
  /** Página exibida (0-based); a fonte da verdade é o state do pai */
  page: number;
  onPageChange(page: number): void;
  onRetry(): void;
}

export function TransactionsTable({
  data,
  loading,
  error,
  categories,
  categoriesLoading,
  page,
  onPageChange,
  onRetry,
}: TransactionsTableProps) {
  const totalPages = data?.page.totalPages ?? 0;
  const isEmpty = data !== null && data.content.length === 0 && page === 0;

  return (
    <section className="overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <header className="flex items-baseline justify-between gap-4 px-5 pt-5 pb-4">
        <h2 className="font-semibold text-zinc-900 dark:text-zinc-50">Transações</h2>
        {data && data.page.totalElements > 0 && (
          <span className="text-sm text-zinc-500 dark:text-zinc-400">
            {pluralize(data.page.totalElements, "transação", "transações")}
          </span>
        )}
      </header>

      {error ? (
        <div role="alert" className="flex flex-col items-center gap-3 px-5 py-12 text-center">
          <AlertCircle className="size-6 text-red-500" aria-hidden />
          <p className="text-sm text-zinc-600 dark:text-zinc-400">{error}</p>
          <button
            type="button"
            onClick={onRetry}
            className="flex items-center gap-1.5 rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 hover:bg-zinc-100 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
          >
            <RefreshCw className="size-3.5" aria-hidden />
            Tentar novamente
          </button>
        </div>
      ) : isEmpty ? (
        <div className="flex flex-col items-center gap-2 px-5 py-14 text-center">
          <Receipt className="size-8 text-zinc-300 dark:text-zinc-700" aria-hidden />
          <p className="text-sm text-zinc-500 dark:text-zinc-400">Nenhuma transação ainda. Importe um extrato acima.</p>
        </div>
      ) : (
        <>
          <table className="w-full table-fixed text-sm">
            <thead className="border-y border-zinc-200 bg-zinc-50 text-left text-xs font-medium tracking-wide text-zinc-500 uppercase dark:border-zinc-800 dark:bg-zinc-950/40 dark:text-zinc-400">
              <tr>
                <th scope="col" className="hidden w-28 py-2.5 pl-5 font-medium sm:table-cell">Data</th>
                <th scope="col" className="py-2.5 pr-3 pl-5 font-medium sm:pl-3">Descrição</th>
                <th scope="col" className="hidden w-44 px-3 py-2.5 font-medium sm:table-cell">Categoria</th>
                <th scope="col" className="w-32 py-2.5 pr-5 text-right font-medium sm:w-36">Valor</th>
              </tr>
            </thead>
            <tbody
              aria-busy={loading}
              className={`divide-y divide-zinc-100 transition-opacity dark:divide-zinc-800 ${loading && data ? "opacity-60" : ""}`}
            >
              {data ? (
                data.content.map((transaction) => (
                  <TransactionRow
                    key={transaction.id}
                    transaction={transaction}
                    category={transaction.categoryId ? categories.get(transaction.categoryId) : undefined}
                    categoriesLoading={categoriesLoading}
                  />
                ))
              ) : (
                <SkeletonRows />
              )}
            </tbody>
          </table>

          <footer className="flex items-center justify-between gap-4 border-t border-zinc-200 px-5 py-3 dark:border-zinc-800">
            <span className="text-sm text-zinc-500 tabular-nums dark:text-zinc-400">
              {totalPages > 0 && `Página ${page + 1} de ${totalPages}`}
            </span>
            <div className="flex gap-2">
              <PageButton onClick={() => onPageChange(page - 1)} disabled={loading || page === 0}>
                <ChevronLeft className="size-4" aria-hidden />
                Anterior
              </PageButton>
              <PageButton onClick={() => onPageChange(page + 1)} disabled={loading || page + 1 >= totalPages}>
                Próximo
                <ChevronRight className="size-4" aria-hidden />
              </PageButton>
            </div>
          </footer>
        </>
      )}
    </section>
  );
}

interface TransactionRowProps {
  transaction: Transaction;
  category: Category | undefined;
  categoriesLoading: boolean;
}

function TransactionRow({ transaction, category, categoriesLoading }: TransactionRowProps) {
  const expense = transaction.type === "EXPENSE";
  const categoryLabel =
    transaction.categoryId === null ? null : (category?.name ?? (categoriesLoading ? "…" : "—"));

  const categoryNode =
    categoryLabel === null ? (
      <span className="text-zinc-400 italic dark:text-zinc-500">Sem categoria</span>
    ) : (
      <span className="inline-block max-w-full truncate rounded-md bg-zinc-100 px-2 py-0.5 align-middle text-xs font-medium text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300">
        {categoryLabel}
      </span>
    );

  return (
    <tr className="transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/40">
      <td className="hidden py-3 pl-5 whitespace-nowrap text-zinc-500 tabular-nums sm:table-cell dark:text-zinc-400">
        {formatDate(transaction.date)}
      </td>
      <td className="py-3 pr-3 pl-5 sm:pl-3">
        <p className="truncate font-medium text-zinc-900 dark:text-zinc-100" title={transaction.description}>
          {transaction.description}
        </p>
        {/* No mobile as colunas Data e Categoria somem: viram uma linha sob a descrição */}
        <p className="mt-1 flex items-center gap-2 truncate text-xs sm:hidden">
          <span className="text-zinc-500 tabular-nums dark:text-zinc-400">{formatDate(transaction.date)}</span>
          {categoryNode}
        </p>
      </td>
      <td className="hidden px-3 py-3 sm:table-cell">{categoryNode}</td>
      <td
        className={`py-3 pr-5 text-right font-medium whitespace-nowrap tabular-nums ${
          expense ? "text-red-600 dark:text-red-400" : "text-emerald-600 dark:text-emerald-400"
        }`}
      >
        {expense ? formatCurrency(-transaction.amount) : `+${formatCurrency(transaction.amount)}`}
      </td>
    </tr>
  );
}

function SkeletonRows() {
  return (
    <>
      {Array.from({ length: 6 }, (_, i) => (
        <tr key={i} className="animate-pulse" aria-hidden>
          <td className="hidden py-3.5 pl-5 sm:table-cell"><div className="h-4 w-16 rounded bg-zinc-200 dark:bg-zinc-800" /></td>
          <td className="py-3.5 pr-3 pl-5 sm:pl-3"><div className="h-4 rounded bg-zinc-200 dark:bg-zinc-800" style={{ width: `${80 - i * 8}%` }} /></td>
          <td className="hidden px-3 py-3.5 sm:table-cell"><div className="h-4 w-20 rounded bg-zinc-200 dark:bg-zinc-800" /></td>
          <td className="py-3.5 pr-5"><div className="ml-auto h-4 w-20 rounded bg-zinc-200 dark:bg-zinc-800" /></td>
        </tr>
      ))}
    </>
  );
}

function PageButton({
  onClick,
  disabled,
  children,
}: {
  onClick(): void;
  disabled: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="flex items-center gap-1 rounded-lg border border-zinc-300 px-3 py-1.5 text-sm font-medium text-zinc-700 transition-colors hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
    >
      {children}
    </button>
  );
}
