import { Scale, TrendingDown, TrendingUp, type LucideIcon } from "lucide-react";

import { formatCurrency } from "@/lib/format";
import type { MonthlyReportResponse } from "@/types";

interface SummaryCardsProps {
  report: MonthlyReportResponse | null;
}

export function SummaryCards({ report }: SummaryCardsProps) {
  const negative = (report?.balance ?? 0) < 0;

  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <SummaryCard
        label="Receitas"
        value={report?.totalIncome}
        icon={TrendingUp}
        iconClass="bg-emerald-50 text-emerald-600 dark:bg-emerald-950/60 dark:text-emerald-400"
      />
      <SummaryCard
        label="Despesas"
        value={report?.totalExpense}
        icon={TrendingDown}
        iconClass="bg-red-50 text-red-600 dark:bg-red-950/60 dark:text-red-400"
      />
      <SummaryCard
        label="Saldo"
        value={report?.balance}
        icon={Scale}
        iconClass="bg-blue-50 text-blue-600 dark:bg-blue-950/60 dark:text-blue-400"
        valueClass={negative ? "text-red-600 dark:text-red-400" : undefined}
        hint={negative ? "Gastou mais do que recebeu" : "Receitas menos despesas"}
      />
    </div>
  );
}

interface SummaryCardProps {
  label: string;
  /** undefined = ainda carregando */
  value: number | undefined;
  icon: LucideIcon;
  iconClass: string;
  valueClass?: string;
  hint?: string;
}

function SummaryCard({ label, value, icon: Icon, iconClass, valueClass, hint }: SummaryCardProps) {
  return (
    <section className="rounded-2xl border border-zinc-200 bg-white p-5 shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium text-zinc-500 dark:text-zinc-400">{label}</h2>
        <span className={`flex size-8 items-center justify-center rounded-lg ${iconClass}`}>
          <Icon className="size-4" aria-hidden />
        </span>
      </div>

      {value === undefined ? (
        <div className="mt-3 h-8 w-32 animate-pulse rounded-md bg-zinc-200 dark:bg-zinc-800" />
      ) : (
        <p
          className={`mt-3 text-2xl font-semibold tracking-tight tabular-nums ${
            valueClass ?? "text-zinc-900 dark:text-zinc-50"
          }`}
        >
          {formatCurrency(value)}
        </p>
      )}

      {hint && <p className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">{hint}</p>}
    </section>
  );
}
