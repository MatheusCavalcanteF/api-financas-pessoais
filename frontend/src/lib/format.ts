// Instância única: criar um Intl.NumberFormat por chamada é caro em listas grandes
const currencyFormatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

/** 1234.5 -> "R$ 1.234,50". Só para exibição; somas vêm prontas do backend. */
export function formatCurrency(value: number): string {
  return currencyFormatter.format(value);
}

/**
 * "2026-10-07" -> "07/10/2026". Monta a string direto, sem `new Date()`:
 * a data vem sem fuso e o construtor a trataria como UTC, voltando um dia no Brasil.
 */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-");
  return `${day}/${month}/${year}`;
}

const integerFormatter = new Intl.NumberFormat("pt-BR");

/** Contagem com plural simples: (1, "transação", "transações") -> "1 transação" */
export function pluralize(count: number, singular: string, plural: string): string {
  return `${integerFormatter.format(count)} ${count === 1 ? singular : plural}`;
}

const percentFormatter = new Intl.NumberFormat("pt-BR", { style: "percent", maximumFractionDigits: 1 });

/** 0.256 -> "25,6%" */
export function formatPercent(ratio: number): string {
  return percentFormatter.format(ratio);
}

const monthFormatter = new Intl.DateTimeFormat("pt-BR", { month: "long", timeZone: "UTC" });

/** Nomes dos meses em pt-BR, capitalizados: índice 0 = "Janeiro" */
export const MONTH_NAMES = Array.from({ length: 12 }, (_, i) => {
  const name = monthFormatter.format(new Date(Date.UTC(2000, i, 1)));
  return name.charAt(0).toUpperCase() + name.slice(1);
});
