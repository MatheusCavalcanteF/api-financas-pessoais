"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";

import { MONTH_NAMES } from "@/lib/format";

export interface Period {
  year: number;
  /** 1–12 */
  month: number;
}

export function currentPeriod(): Period {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1 };
}

function shiftMonth({ year, month }: Period, delta: number): Period {
  const index = year * 12 + (month - 1) + delta;
  return { year: Math.floor(index / 12), month: (index % 12) + 1 };
}

interface PeriodPickerProps {
  value: Period;
  onChange(period: Period): void;
  /** Ano corrente, para montar a lista de anos */
  currentYear: number;
}

export function PeriodPicker({ value, onChange, currentYear }: PeriodPickerProps) {
  // Últimos 10 anos + o próximo; estende se as setas levarem para fora da faixa
  const firstYear = Math.min(currentYear - 10, value.year);
  const lastYear = Math.max(currentYear + 1, value.year);
  const years = Array.from({ length: lastYear - firstYear + 1 }, (_, i) => lastYear - i);

  return (
    <div className="flex items-center gap-1 rounded-xl border border-zinc-200 bg-white p-1 shadow-xs dark:border-zinc-800 dark:bg-zinc-900">
      <button
        type="button"
        onClick={() => onChange(shiftMonth(value, -1))}
        aria-label="Mês anterior"
        className={arrowClass}
      >
        <ChevronLeft className="size-4" aria-hidden />
      </button>

      <select
        aria-label="Mês"
        value={value.month}
        onChange={(e) => onChange({ ...value, month: Number(e.target.value) })}
        className={selectClass}
      >
        {MONTH_NAMES.map((name, i) => (
          <option key={name} value={i + 1}>
            {name}
          </option>
        ))}
      </select>

      <select
        aria-label="Ano"
        value={value.year}
        onChange={(e) => onChange({ ...value, year: Number(e.target.value) })}
        className={`${selectClass} tabular-nums`}
      >
        {years.map((year) => (
          <option key={year} value={year}>
            {year}
          </option>
        ))}
      </select>

      <button
        type="button"
        onClick={() => onChange(shiftMonth(value, 1))}
        aria-label="Próximo mês"
        className={arrowClass}
      >
        <ChevronRight className="size-4" aria-hidden />
      </button>
    </div>
  );
}

const arrowClass =
  "rounded-lg p-1.5 text-zinc-500 transition-colors hover:bg-zinc-100 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100";

const selectClass =
  "cursor-pointer rounded-lg bg-transparent px-2 py-1 text-sm font-medium text-zinc-900 hover:bg-zinc-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-600/40 dark:text-zinc-100 dark:hover:bg-zinc-800 dark:[color-scheme:dark]";
