import { getMonthlyReport } from "@/lib/api";

import { useApiQuery } from "./use-api-query";

/** Relatório do mês; refaz a busca quando o período muda. */
export function useMonthlyReport(year: number, month: number) {
  return useApiQuery(`report:${year}-${month}`, (signal) => getMonthlyReport(year, month, { signal }));
}
