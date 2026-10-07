import { PieChart } from "lucide-react";

import { formatCurrency, formatPercent } from "@/lib/format";
import type { MonthlyReportResponse } from "@/types";

interface CategoryBreakdownProps {
  report: MonthlyReportResponse | null;
}

export function CategoryBreakdown({ report }: CategoryBreakdownProps) {
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <header className="mb-5 flex items-baseline justify-between gap-4">
        <h2 className="font-semibold text-zinc-900 dark:text-zinc-50">Despesas por categoria</h2>
        {report && report.totalExpense > 0 && (
          <span className="text-sm text-zinc-500 tabular-nums dark:text-zinc-400">
            Total {formatCurrency(report.totalExpense)}
          </span>
        )}
      </header>

      {!report ? (
        <BreakdownSkeleton />
      ) : report.expensesByCategory.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-10 text-center">
          <PieChart className="size-8 text-zinc-300 dark:text-zinc-700" aria-hidden />
          <p className="text-sm text-zinc-500 dark:text-zinc-400">Nenhuma despesa registrada neste mês.</p>
        </div>
      ) : (
        <ul className="flex flex-col gap-4">
          {report.expensesByCategory.map((item) => {
            // O backend garante que a soma dos totais é totalExpense; o clamp só
            // protege a barra de arredondamentos
            const ratio = report.totalExpense > 0 ? Math.min(item.total / report.totalExpense, 1) : 0;
            const uncategorized = item.categoryId === null;

            return (
              <li key={item.categoryId ?? "uncategorized"}>
                <div className="mb-1.5 flex items-baseline justify-between gap-4 text-sm">
                  <span
                    className={`truncate ${
                      uncategorized
                        ? "text-zinc-500 italic dark:text-zinc-400"
                        : "font-medium text-zinc-800 dark:text-zinc-200"
                    }`}
                  >
                    {item.categoryName}
                  </span>
                  <span className="shrink-0 tabular-nums">
                    <span className="font-medium text-zinc-900 dark:text-zinc-100">{formatCurrency(item.total)}</span>
                    <span className="ml-2 inline-block w-14 text-right text-zinc-500 dark:text-zinc-400">
                      {formatPercent(ratio)}
                    </span>
                  </span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800" aria-hidden>
                  <div
                    style={{ width: `${ratio * 100}%` }}
                    className={`h-full rounded-full transition-[width] duration-500 ${
                      uncategorized ? "bg-zinc-400 dark:bg-zinc-500" : "bg-blue-500"
                    }`}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function BreakdownSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-hidden>
      {[70, 45, 25].map((width) => (
        <div key={width} className="animate-pulse">
          <div className="mb-2 flex justify-between">
            <div className="h-4 w-28 rounded bg-zinc-200 dark:bg-zinc-800" />
            <div className="h-4 w-20 rounded bg-zinc-200 dark:bg-zinc-800" />
          </div>
          <div className="h-2 rounded-full bg-zinc-100 dark:bg-zinc-800">
            <div style={{ width: `${width}%` }} className="h-full rounded-full bg-zinc-200 dark:bg-zinc-700" />
          </div>
        </div>
      ))}
    </div>
  );
}
