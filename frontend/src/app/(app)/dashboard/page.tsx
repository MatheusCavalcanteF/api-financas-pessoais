"use client";

import { AlertCircle, RefreshCw } from "lucide-react";
import { useState } from "react";

import { CategoryBreakdown } from "@/components/dashboard/category-breakdown";
import { SummaryCards } from "@/components/dashboard/summary-cards";
import { currentPeriod, PeriodPicker, type Period } from "@/components/period-picker";
import { useMonthlyReport } from "@/hooks/use-monthly-report";
import { MONTH_NAMES } from "@/lib/format";

export default function DashboardPage() {
  // Inicializador lazy: a data é lida no browser (a área logada não renderiza no servidor)
  const [today] = useState(currentPeriod);
  const [period, setPeriod] = useState<Period>(today);
  const { data, error, loading, reload } = useMonthlyReport(period.year, period.month);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-50">Dashboard</h1>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">
            Resumo de {MONTH_NAMES[period.month - 1].toLowerCase()} de {period.year}
          </p>
        </div>
        <PeriodPicker value={period} onChange={setPeriod} currentYear={today.year} />
      </div>

      {error && (
        <div
          role="alert"
          className="flex items-center justify-between gap-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/50 dark:text-red-300"
        >
          <span className="flex items-center gap-2">
            <AlertCircle className="size-4 shrink-0" aria-hidden />
            {error}
          </span>
          <button
            type="button"
            onClick={reload}
            className="flex shrink-0 items-center gap-1.5 rounded-lg px-2.5 py-1 font-medium hover:bg-red-100 dark:hover:bg-red-900/50"
          >
            <RefreshCw className="size-3.5" aria-hidden />
            Tentar novamente
          </button>
        </div>
      )}

      {/* Ao trocar de mês, o relatório anterior fica esmaecido até o novo chegar */}
      {!(error && !data) && (
        <div
          aria-busy={loading}
          className={`flex flex-col gap-6 transition-opacity ${loading && data ? "opacity-60" : ""}`}
        >
          <SummaryCards report={data} />
          <CategoryBreakdown report={data} />
        </div>
      )}
    </div>
  );
}
